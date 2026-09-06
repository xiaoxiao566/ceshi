package com.ceshi.printtool.print

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

object UsbPrinterDetector {

    const val HP_VENDOR_ID = 0x03f0
    const val HP_LASERJET_1020_PID = 0x2b17
    const val PRINTER_CLASS = 0x07

    /** 是否为打印机类设备（含 HP 供应商识别回退） */
    fun isPrinter(device: UsbDevice): Boolean {
        if (device.vendorId == HP_VENDOR_ID) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == PRINTER_CLASS) return true
        }
        return false
    }

    /** 是否为目标打印机 HP LaserJet 1020 */
    fun isHp1020(device: UsbDevice): Boolean =
        device.vendorId == HP_VENDOR_ID && device.productId == HP_LASERJET_1020_PID

    /** 列出当前连接的打印机 */
    fun detect(usbManager: UsbManager): List<UsbDevice> =
        usbManager.deviceList.values.filter { isPrinter(it) }
}