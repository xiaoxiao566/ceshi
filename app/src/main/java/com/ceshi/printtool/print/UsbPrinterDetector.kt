package com.ceshi.printtool.print

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

// 认 USB 打印机：普通激光走 PCL，HP 1020 这种没固件的走「固件 + ZJS」。
object UsbPrinterDetector {

    const val HP_VENDOR_ID = 0x03f0
    const val HP_LASERJET_1020_PID = 0x2b17
    const val PRINTER_CLASS = 0x07

    enum class Backend {
        // 普通激光走 PCL5
        PCL,

        // HP 主机型，先推固件再走 ZJS
        HP_HOST_BASED_ZJS
    }

    data class PrinterInfo(
        val device: UsbDevice,
        val brand: String,
        val backend: Backend
    )

    fun isPrinter(device: UsbDevice): Boolean {
        if (PrinterBrands.isKnownVendor(device.vendorId)) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == PRINTER_CLASS) return true
        }
        return false
    }

    fun isHp1020(device: UsbDevice): Boolean =
        device.vendorId == HP_VENDOR_ID && device.productId == HP_LASERJET_1020_PID

    fun classify(device: UsbDevice): PrinterInfo {
        val brand = PrinterBrands.brandName(device.vendorId) ?: "未知品牌"
        val backend = when {
            isHp1020(device) -> Backend.HP_HOST_BASED_ZJS
            else -> Backend.PCL
        }
        return PrinterInfo(device, brand, backend)
    }

    fun detect(usbManager: UsbManager): List<PrinterInfo> =
        usbManager.deviceList.values.filter { isPrinter(it) }.map { classify(it) }
}