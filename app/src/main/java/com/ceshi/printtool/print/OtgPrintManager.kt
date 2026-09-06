package com.ceshi.printtool.print

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.ceshi.printtool.document.DocumentSource
import com.ceshi.printtool.print.UsbPrinterDetector.Backend
import com.ceshi.printtool.print.UsbPrinterDetector.PrinterInfo
import java.util.concurrent.Executors

sealed class PrintResult {
    data class Success(val message: String) : PrintResult()
    data class Failure(val message: String) : PrintResult()
    data class Info(val message: String) : PrintResult()
}

/**
 * OTG / USB 直连打印。
 *
 * 按识别到的品牌/后端路由：
 *  - PCL：绝大多数激光打印机（HP/Brother/Canon/Samsung/Kyocera/Lexmark/Xerox/Ricoh 等）
 *    走 PCL5 单色栅格，逐页发送；
 *  - HP 主机型 GDI（LaserJet 1020 等）：先上传固件复位，等待重新枚举后（接入 ZJS 编码）发送栅格。
 *
 * 彩色喷墨（Epson/Canon 家用喷墨）及 Canon UFR / Samsung SPL 等专有主机型协议，
 * 建议改用无线打印（系统打印框架 + IPP，已完整支持）。
 *
 * 页面以 A4 @300dpi 渲染。
 */
class OtgPrintManager(private val context: Context) {

    companion object {
        private const val OTG_DPI = 300
        private const val A4_WIDTH_PX = 2480
        private const val A4_HEIGHT_PX = 3508
        private const val RESET_WAIT_MS = 2500L
        private const val ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_PERMISSION"
    }

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val executor = Executors.newSingleThreadExecutor()
    private var permissionReceiver: BroadcastReceiver? = null

    fun isUsbHostSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    /** 发起一次 OTG 打印；结果通过回调返回（可能在后台线程） */
    fun print(source: DocumentSource, jobName: String, options: PrintOptions = PrintOptions(), onResult: (PrintResult) -> Unit) {
        val printers = UsbPrinterDetector.detect(usbManager)
        if (printers.isEmpty()) {
            onResult(PrintResult.Failure("未检测到 USB 打印机：请确认打印机已开机并通过 OTG 线连接"))
            source.close()
            return
        }
        // 优先 HP 主机型（需固件），否则取第一台
        val info = printers.firstOrNull { it.backend == Backend.HP_HOST_BASED_ZJS } ?: printers.first()
        withPermission(info.device) { granted ->
            if (granted) {
                executor.execute { doPrint(info, source, options, onResult) }
            } else {
                onResult(PrintResult.Failure("未获得「${info.brand}」的 USB 设备访问权限"))
                source.close()
            }
        }
    }

    private fun withPermission(device: UsbDevice, callback: (Boolean) -> Unit) {
        if (usbManager.hasPermission(device)) {
            callback(true)
            return
        }
        val action = ACTION_USB_PERMISSION
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == action) {
                    ctx?.unregisterReceiver(this)
                    permissionReceiver = null
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    callback(granted)
                }
            }
        }
        permissionReceiver = receiver
        context.registerReceiver(receiver, IntentFilter(action))
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, pi)
    }

    private fun doPrint(
        info: PrinterInfo,
        source: DocumentSource,
        options: PrintOptions,
        onResult: (PrintResult) -> Unit
    ) {
        try {
            when (info.backend) {
                Backend.HP_HOST_BASED_ZJS -> printHpHostBased(info, source, onResult)
                Backend.PCL -> printPcl(info, source, options, onResult)
            }
        } catch (e: UnsupportedOperationException) {
            onResult(PrintResult.Info(e.message ?: "「${info.brand}」该打印后端尚未支持"))
        } catch (e: Exception) {
            onResult(PrintResult.Failure("${info.brand} 打印失败：${e.message ?: e.javaClass.simpleName}"))
        } finally {
            source.close()
        }
    }

    /**
     * HP 主机型 GDI（如 1020）：上传固件 → 复位 →（接入 ZJS 后）发送栅格。
     */
    private fun printHpHostBased(
        info: PrinterInfo,
        source: DocumentSource,
        onResult: (PrintResult) -> Unit
    ) {
        val fw = Hp1020Firmware.ensure(context)
            ?: run {
                onResult(PrintResult.Failure("${info.brand} 需要固件 ${Hp1020Firmware.FIRMWARE_NAME}，请联网后重试"))
                return
            }

        var conn = openWithInterface(info.device) ?: run { onResult(PrintResult.Failure("无法打开 USB 设备")); return }
        val uploaded = try {
            Hp1020Firmware.upload(conn.first, conn.second, fw)
        } finally {
            closeQuietly(conn.first, conn.third)
        }
        if (!uploaded) {
            onResult(PrintResult.Failure("${info.brand} 固件上传失败"))
            return
        }

        Thread.sleep(RESET_WAIT_MS)
        val ready = openWithInterface(info.device)
        if (ready == null) {
            onResult(PrintResult.Info("已向 ${info.brand} 上传固件并完成复位。"))
            return
        }
        try {
            val bmp = source.renderPage(0, A4_WIDTH_PX, A4_HEIGHT_PX)
            val data = ZjsEncoder.encodeMonochrome(bmp, OTG_DPI)
            bmp.recycle()
            sendAll(ready.first, ready.second, data)
            onResult(PrintResult.Success("已向 ${info.brand} 发送打印数据"))
        } finally {
            closeQuietly(ready.first, ready.third)
        }
    }

    /** PCL 打印机：逐页渲染 → PCL5 单色 → 发送 */
    private fun printPcl(info: PrinterInfo, source: DocumentSource, options: PrintOptions, onResult: (PrintResult) -> Unit) {
        val open = openWithInterface(info.device) ?: run { onResult(PrintResult.Failure("无法打开 USB 设备")); return }
        val conn = open.first
        val out = open.second
        val intf = open.third
        try {
            var sent = 0
            for (p in 0 until source.pageCount) {
                val bmp = source.renderPage(p, A4_WIDTH_PX, A4_HEIGHT_PX)
                val data = PclEncoder.encodeMonochrome(bmp, OTG_DPI, options.paperSize, options.copies, options.duplex)
                bmp.recycle()
                if (!sendAll(conn, out, data)) {
                    onResult(PrintResult.Failure("数据传输中断（第 ${p + 1} 页）"))
                    return
                }
                sent++
            }
            onResult(
                PrintResult.Success(
                    "已发送 $sent 页到 ${info.brand}（PCL）。" +
                        if (info.brand.startsWith("Epson")) " 若为爱普生喷墨机型请改用无线打印。" else ""
                )
            )
        } finally {
            closeQuietly(conn, intf)
        }
    }

    // ---------- USB IO 工具 ----------

    private fun findPrinterInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbPrinterDetector.PRINTER_CLASS) return intf
        }
        return if (device.interfaceCount > 0) device.getInterface(0) else null
    }

    private fun findBulkOut(intf: UsbInterface): UsbEndpoint? {
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                return ep
            }
        }
        return null
    }

    /** 打开设备并占用打印接口，返回 (connection, bulkOut, interface) */
    private fun openWithInterface(device: UsbDevice): Triple<UsbDeviceConnection, UsbEndpoint, UsbInterface>? {
        val intf = findPrinterInterface(device) ?: return null
        val out = findBulkOut(intf) ?: return null
        val conn = usbManager.openDevice(device) ?: return null
        if (!conn.claimInterface(intf, true)) {
            conn.close()
            return null
        }
        return Triple(conn, out, intf)
    }

    private fun sendAll(conn: UsbDeviceConnection, out: UsbEndpoint, data: ByteArray): Boolean {
        var off = 0
        val chunk = out.maxPacketSize.coerceAtLeast(64)
        while (off < data.size) {
            val len = minOf(chunk, data.size - off)
            val sent = conn.bulkTransfer(out, data, off, len, 5000)
            if (sent < 0) return false
            off += sent
        }
        return true
    }

    private fun closeQuietly(conn: UsbDeviceConnection?, intf: UsbInterface?) {
        if (conn == null) return
        try {
            if (intf != null) conn.releaseInterface(intf)
        } catch (_: Exception) {
        }
        conn.close()
    }
}