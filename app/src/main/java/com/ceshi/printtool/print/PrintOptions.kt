package com.ceshi.printtool.print

/**
 * 打印选项（无线与 USB/OTG 共用）。
 *
 * @param duplex    是否双面打印
 * @param copies    份数（1–99）
 * @param paperSize 纸张大小："A4" / "Letter" / "Legal"
 * @param color     是否彩色（false = 黑白）；OTG 的 PCL5 后端目前仅支持单色
 */
data class PrintOptions(
    val duplex: Boolean = false,
    val copies: Int = 1,
    val paperSize: String = "A4",
    val color: Boolean = true
)