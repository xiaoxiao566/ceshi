package com.ceshi.printtool.print

/**
 * 常见打印机品牌与 USB 供应商 ID（vendor ID）。
 *
 * 说明：
 *  - 无线打印（Android 系统打印框架 + IPP / Mopria / 各品牌打印插件）对下表所有品牌
 *    都适用，无需依赖本表；这也是彩色喷墨、主机型等复杂机型最可靠的路径。
 *  - 本表仅用于 USB / OTG 直连打印时的品牌识别与协议路由。
 */
object PrinterBrands {

    // 常见厂商 Vendor ID（IEEE 分配的官方 USB vendor ID）
    const val HP = 0x03F0
    const val CANON = 0x04A9
    const val EPSON = 0x04B8
    const val BROTHER = 0x04F9
    const val SAMSUNG = 0x04E8
    const val KYOCERA = 0x0482
    const val LEXMARK = 0x043D
    const val XEROX = 0x0924
    const val RICOH = 0x05CA
    const val DELL = 0x413C

    private val NAMES = mapOf(
        HP to "HP（惠普）",
        CANON to "Canon（佳能）",
        EPSON to "Epson（爱普生）",
        BROTHER to "Brother（兄弟）",
        SAMSUNG to "Samsung（三星）",
        KYOCERA to "Kyocera（京瓷）",
        LEXMARK to "Lexmark（利盟）",
        XEROX to "Xerox（施乐）",
        RICOH to "Ricoh（理光）",
        DELL to "Dell（戴尔）"
    )

    /** 依据 vendorId 返回品牌中文名；未知返回 null */
    fun brandName(vendorId: Int): String? = NAMES[vendorId]

    /** 是否为已知品牌（用于展示与路由） */
    fun isKnownVendor(vendorId: Int): Boolean = NAMES.containsKey(vendorId)
}