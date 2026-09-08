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
import androidx.core.content.ContextCompat
import com.ceshi.printtool.document.DocumentSource
import com.ceshi.printtool.print.UsbPrinterDetector.Backend
import com.ceshi.printtool.print.UsbPrinterDetector.PrinterInfo
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

sealed class PrintResult {
    data class Success(val message: String) : PrintResult()
    data class Failure(val message: String) : PrintResult()
    data class Info(val message: String) : PrintResult()
}

// OTG 直连打印。按认出来的机型走：普通激光给 PCL5，HP 1020 先推固件。
// 页面统一按 A4@300dpi 画。
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
    private val permissionRequestCode = AtomicInteger(1)
    private var deviceListenerReceiver: BroadcastReceiver? = null

    fun isUsbHostSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    // 列出当前能直接用的 USB 打印机
    fun detectPrinters(): List<PrinterInfo> = UsbPrinterDetector.detect(usbManager)

    fun print(source: DocumentSource, jobName: String, options: PrintOptions = PrintOptions(), onResult: (PrintResult) -> Unit) {
        val printers = detectPrinters()
        if (printers.isEmpty()) {
            onResult(PrintResult.Failure("没找到 USB 打印机，看下是不是没开机或者 OTG 没插好"))
            source.close()
            return
        }
        // 有 HP 主机型就先用它，没有就用列表里第一台
        val info = printers.firstOrNull { it.backend == Backend.HP_HOST_BASED_ZJS } ?: printers.first()
        print(info, source, jobName, options, onResult)
    }

    // 指定某台打印机打
    fun print(info: PrinterInfo, source: DocumentSource, jobName: String, options: PrintOptions = PrintOptions(), onResult: (PrintResult) -> Unit) {
        withPermission(info.device) { granted ->
            if (granted) {
                executor.execute { doPrint(info, source, options, onResult) }
            } else {
                onResult(PrintResult.Failure("没拿到「${info.brand}」的 USB 权限"))
                source.close()
            }
        }
    }

    // 监听 USB 打印机插拔，返回一个关闭监听的函数，Context 销毁时记得调
    fun listenForDeviceChanges(listener: () -> Unit): () -> Unit {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> listener()
                }
            }
        }
        deviceListenerReceiver = receiver
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
        )
        return { unregisterReceiverSafely(receiver); deviceListenerReceiver?.let { if (it === receiver) deviceListenerReceiver = null } }
    }

    private fun unregisterReceiverSafely(receiver: BroadcastReceiver?) {
        if (receiver == null) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // 已经注销过了，忽略
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
                ctx ?: return
                if (intent?.action == action) {
                    unregisterReceiverSafely(this)
                    if (permissionReceiver === this) permissionReceiver = null
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    callback(granted)
                }
            }
        }
        // 换新请求前把上一次的 receiver 清掉，避免积压
        unregisterReceiverSafely(permissionReceiver)
        permissionReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val code = permissionRequestCode.getAndIncrement()
        val pi = PendingIntent.getBroadcast(
            context, code, Intent(action),
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
                Backend.HP_HOST_BASED_ZJS -> printHpHostBased(info, source, options, onResult)
                Backend.PCL -> printPcl(info, source, options, onResult)
            }
        } catch (e: UnsupportedOperationException) {
            onResult(PrintResult.Info(e.message ?: "「${info.brand}」这个还打不了"))
        } catch (e: Exception) {
            onResult(PrintResult.Failure("${info.brand} 打印失败：${e.message ?: e.javaClass.simpleName}"))
        } finally {
            source.close()
        }
    }

    private fun printHpHostBased(
        info: PrinterInfo,
        source: DocumentSource,
        options: PrintOptions,
        onResult: (PrintResult) -> Unit
    ) {
        val fw = Hp1020Firmware.ensure(context)
            ?: run {
                onResult(PrintResult.Failure("${info.brand} 得先下固件 ${Hp1020Firmware.FIRMWARE_NAME}，联网再试"))
                return
            }

        var conn = openWithInterface(info.device) ?: run { onResult(PrintResult.Failure("USB 设备打不开")); return }
        val uploaded = try {
            Hp1020Firmware.upload(conn.first, conn.second, fw)
        } finally {
            closeQuietly(conn.first, conn.third)
        }
        if (!uploaded) {
            onResult(PrintResult.Failure("${info.brand} 固件没推上去"))
            return
        }

        Thread.sleep(RESET_WAIT_MS)
        val ready = openWithInterface(info.device)
        if (ready == null) {
            onResult(PrintResult.Info("固件推给了 ${info.brand}，它自己重启了一下。"))
            return
        }
        try {
            val data = ZjsEncoder.encodeDocument(source, options)
            if (!sendAll(ready.first, ready.second, data)) {
                onResult(PrintResult.Failure("${info.brand} 数据传一半断了"))
                return
            }
            onResult(PrintResult.Success("往 ${info.brand} 发了 ${source.pageCount} 页（ZjStream）"))
        } finally {
            closeQuietly(ready.first, ready.third)
        }
    }

    private fun printPcl(info: PrinterInfo, source: DocumentSource, options: PrintOptions, onResult: (PrintResult) -> Unit) {
        val open = openWithInterface(info.device) ?: run { onResult(PrintResult.Failure("USB 设备打不开")); return }
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
                    onResult(PrintResult.Failure("第 ${p + 1} 页数据断了"))
                    return
                }
                sent++
            }
            onResult(
                PrintResult.Success(
                    "往 ${info.brand} 发了 $sent 页（PCL）。" +
                        if (info.brand.startsWith("Epson")) "爱普生喷墨的话还是走无线吧。" else ""
                )
            )
        } finally {
            closeQuietly(conn, intf)
        }
    }

    // --- 一些 USB 读写的小函数 ---

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