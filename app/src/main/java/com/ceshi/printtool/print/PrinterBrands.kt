package com.ceshi.printtool.print

// 几个常见厂商的 VID，OTG 直连时认品牌用；无线打印根本用不上这张表。
object PrinterBrands {

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

    fun brandName(vendorId: Int): String? = NAMES[vendorId]

    fun isKnownVendor(vendorId: Int): Boolean = NAMES.containsKey(vendorId)
}