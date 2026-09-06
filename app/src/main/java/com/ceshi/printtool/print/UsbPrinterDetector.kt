package com.ceshi.printtool.print

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * USB 打印机的设备识别与协议归类。
 *
 * 打印后端（Backend）分两类：
 *  - PCL5 单色栅格：适配具备 PCL 解释器的激光打印机（HP/Brother/Canon 激光/Samsung/
 *    Kyocera/Lexmark/Xerox/Ricoh 等绝大多数打印机类设备都兼容 PCL）。
 *  - HP_HOST_BASED：HP 主机型（GDI）打印机（如 LaserJet 1020/1005/1018/P1005 等），
 *    无内置固件，需先上传固件再走 ZjStream 栅格。
 */
object UsbPrinterDetector {

    const val HP_VENDOR_ID = 0x03f0
    const val HP_LASERJET_1020_PID = 0x2b17
    const val PRINTER_CLASS = 0x07

    enum class Backend {
        /** 通用 PCL5 激光打印后端 */
        PCL,

        /** HP 主机型 GDI：固件上传 + ZjStream（ZJS）栅格 */
        HP_HOST_BASED_ZJS
    }

    /** 识别结果：设备 + 品牌 + 后端 */
    data class PrinterInfo(
        val device: UsbDevice,
        val brand: String,
        val backend: Backend
    )

    /** 是否为打印机类设备（含已知打印品牌供应商回退） */
    fun isPrinter(device: UsbDevice): Boolean {
        if (PrinterBrands.isKnownVendor(device.vendorId)) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == PRINTER_CLASS) return true
        }
        return false
    }

    /** 是否为 HP LaserJet 1020（主机型，需 sihp1020.dl 固件） */
    fun isHp1020(device: UsbDevice): Boolean =
        device.vendorId == HP_VENDOR_ID && device.productId == HP_LASERJET_1020_PID

    /** 归类：返回品牌名与推荐打印后端 */
    fun classify(device: UsbDevice): PrinterInfo {
        val brand = PrinterBrands.brandName(device.vendorId) ?: "未知品牌"
        val backend = when {
            isHp1020(device) -> Backend.HP_HOST_BASED_ZJS
            else -> Backend.PCL
        }
        return PrinterInfo(device, brand, backend)
    }

    /** 列出当前连接的打印机（按识别结果） */
    fun detect(usbManager: UsbManager): List<PrinterInfo> =
        usbManager.deviceList.values.filter { isPrinter(it) }.map { classify(it) }
}