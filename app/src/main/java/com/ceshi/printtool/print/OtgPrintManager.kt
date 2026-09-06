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
import java.util.concurrent.Executors

sealed class PrintResult {
    data class Success(val message: String) : PrintResult()
    data class Failure(val message: String) : PrintResult()
    data class Info(val message: String) : PrintResult()
}

/**
 * OTG / USB 打印。
 *
 * 支持两类设备：
 *  - HP LaserJet 1020：先上传固件复位，随后（接入 ZJS 编码后）发送栅格数据；
 *  - 其余打印机类设备：直接按 PCL5 单色栅格逐页发送。
 *
 * 页面以 A4 @300dpi 渲染。
 */
class OtgPrintManager(private val context: Context) {

    companion object {
        private const val OTG_DPI = 300
        private const val A4_WIDTH_PX = 2480   // A4 ?? 300dpi
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
    fun print(source: DocumentSource, jobName: String, onResult: (PrintResult) -> Unit) {
        val printers = UsbPrinterDetector.detect(usbManager)
        if (printers.isEmpty()) {
            onResult(PrintResult.Failure("未检测到 USB 打印机：请确认打印机已开机并通过 OTG 线连接"))
            source.close()
            return
        }
        val device = printers.firstOrNull { UsbPrinterDetector.isHp1020(it) } ?: printers.first()
        withPermission(device) { granted ->
            if (granted) {
                executor.execute { doPrint(device, source, jobName, onResult) }
            } else {
                onResult(PrintResult.Failure("未获得 USB 设备访问权限"))
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

    private fun doPrint(device: UsbDevice, source: DocumentSource, jobName: String, onResult: (PrintResult) -> Unit) {
        try {
            val isHp1020 = UsbPrinterDetector.isHp1020(device)
            if (isHp1020) {
                printHp1020(device, source, onResult)
            } else {
                printPcl(device, source, onResult)
            }
        } catch (e: UnsupportedOperationException) {
            onResult(PrintResult.Info(e.message ?: "该设备打印后端尚未支持"))
        } catch (e: Exception) {
            onResult(PrintResult.Failure(e.message ?: e.javaClass.simpleName))
        } finally {
            source.close()
        }
    }

    /**
     * HP 1020：上传固件 → 复位 → （接入 ZJS 后）发送栅格。
     */
    private fun printHp1020(device: UsbDevice, source: DocumentSource, onResult: (PrintResult) -> Unit) {
        val fw = Hp1020Firmware.ensure(context)
            ?: run { onResult(PrintResult.Failure("HP 1020 需要固件 ${Hp1020Firmware.FIRMWARE_NAME}，请联网后重试")); return }

        // 阶段一：上传固件
        var conn = openWithInterface(device) ?: run { onResult(PrintResult.Failure("无法打开 USB 设备")); return }
        val uploaded = try {
            Hp1020Firmware.upload(conn.first, conn.second, fw)
        } finally {
            closeQuietly(conn.first, conn.third)
        }
        if (!uploaded) {
            onResult(PrintResult.Failure("HP 1020 固件上传失败"))
            return
        }

        // 阶段二：等待打印机复位并重新枚举
        Thread.sleep(RESET_WAIT_MS)
        val ready = openWithInterface(device)
        if (ready == null) {
            onResult(PrintResult.Info("已向 HP 1020 上传固件并完成复位。"))
            return
        }
        try {
            // 阶段三：发送打印数据（ZJS 编码接入后替换）
            val bmp = source.renderPage(0, A4_WIDTH_PX, A4_HEIGHT_PX)
            val data = ZjsEncoder.encodeMonochrome(bmp, OTG_DPI)
            bmp.recycle()
            sendAll(ready.first, ready.second, data)
            onResult(PrintResult.Success("已发送打印数据"))
        } finally {
            closeQuietly(ready.first, ready.third)
        }
    }

    /** PCL 打印机：逐页渲染 → PCL5 单色 → 发送 */
    private fun printPcl(device: UsbDevice, source: DocumentSource, onResult: (PrintResult) -> Unit) {
        val open = openWithInterface(device) ?: run { onResult(PrintResult.Failure("无法打开 USB 设备")); return }
        val conn = open.first
        val out = open.second
        val intf = open.third
        try {
            var sent = 0
            for (p in 0 until source.pageCount) {
                val bmp = source.renderPage(p, A4_WIDTH_PX, A4_HEIGHT_PX)
                val data = PclEncoder.encodeMonochrome(bmp, OTG_DPI, "A4")
                bmp.recycle()
                if (!sendAll(conn, out, data)) {
                    onResult(PrintResult.Failure("数据传输中断（第 ${p + 1} 页）"))
                    return
                }
                sent++
            }
            onResult(PrintResult.Success("已发送 $sent 页到打印机"))
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