package com.ceshi.printtool.print

// 打印选项，无线和 OTG 通用。PCL5 那路现在只能打黑白，所以“彩色”对 USB 直连暂时没差。
data class PrintOptions(
    val duplex: Boolean = false,
    val copies: Int = 1,
    val paperSize: String = "A4",
    val color: Boolean = true
)