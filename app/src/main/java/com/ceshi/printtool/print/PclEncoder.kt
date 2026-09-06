package com.ceshi.printtool.print

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

/**
 * PCL5 单色栅格编码器。
 *
 * 适用于具备 PCL 解释器的常见 USB 激光打印机（Brother/Pantum/Kyocera 等众多
 * 打印机类设备都兼容 PCL5）。将位图按亮度阈值转为 1-bit 点阵，按行发送。
 */
object PclEncoder {

    private const val ESC = '\u001b'

    fun encodeMonochrome(bitmap: Bitmap, dpi: Int, pageSize: String = "A4"): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val bytesPerRow = (w + 7) / 8
        val out = ByteArrayOutputStream(128 * 1024)

        fun cmd(s: String) {
            out.write(s.toByteArray(Charsets.US_ASCII))
        }

        // 通用退出语言 / 复位
        cmd("${ESC}%-12345X")
        // 页面方向与边距
        cmd("${ESC}&l0O")                       // 纵向
        cmd("${ESC}&l0E")                       // 上边距 0
        cmd(if (pageSize == "A4") "${ESC}&l26A" else "${ESC}&l2A") // A4 / Letter
        // 栅格参数
        cmd("${ESC}*t${dpi}R")                  // 栅格分辨率
        cmd("${ESC}*b0M")                       // 无压缩
        cmd("${ESC}*r${w}S")                    // 源栅格宽度（点）
        cmd("${ESC}*r${h}T")                    // 栅格高度（行）
        cmd("${ESC}*r1A")                       // 开始栅格图形

        val row = ByteArray(bytesPerRow)
        for (y in 0 until h) {
            java.util.Arrays.fill(row, 0)
            for (x in 0 until w) {
                val p = bitmap.getPixel(x, y)
                val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                // 亮度 < 128 视为黑点，置 1
                if (lum < 128) {
                    row[x / 8] = (row[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
            cmd("${ESC}*b${bytesPerRow}W")
            out.write(row)
        }

        cmd("${ESC}*rB")   // 结束栅格图形
        cmd("\u000c")       // 换页（出纸）
        return out.toByteArray()
    }
}