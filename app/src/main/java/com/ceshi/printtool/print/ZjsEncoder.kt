package com.ceshi.printtool.print

import android.graphics.Bitmap
import com.ceshi.printtool.document.DocumentSource
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// HP 1020 直连打印的 ZjStream 编码。1020 是 GDI 机型，没内置解释器，
// 得先推固件、再把页面压成 JBIG 塞进 ZJS 流，这层就是干这个的。
// JBIG 压缩用自带的 libzjsencode.so（jbig-kit 移植），Kotlin 负责拼 ZJS 容器。
object ZjsEncoder {

    // 1020 引擎是 600dpi，这里就按 600x600 出图。
    const val RES_X = 600
    const val RES_Y = 600

    // 常见纸张的毫米尺寸，用来算页面对应像素。
    private val PAPERS = mapOf(
        "A4" to (210 to 297),
        "Letter" to (216 to 279),
        "Legal" to (216 to 356),
        "A5" to (148 to 210),
        "B5" to (182 to 257),
        "A3" to (297 to 420)
    )

    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        try {
            System.loadLibrary("zjsencode")
            loaded = true
        } catch (e: UnsatisfiedLinkError) {
            throw IllegalStateException("没找到 HP 1020 的打印库 libzjsencode.so，可能是这台设备架构不支持：${e.message}")
        }
    }

    // native 侧：把一页单色点阵编成 ZJS 页信封（START_PAGE .. END_PAGE）。
    // 返回可空，native 失败时会传 null 回来。
    @JvmStatic external fun nativeEncodePage(
        bitmap: ByteArray,
        width: Int,
        height: Int,
        resX: Int,
        resY: Int,
        paper: Int,
        copies: Int,
        duplex: Int
    ): ByteArray?

    fun encodeDocument(source: DocumentSource, options: PrintOptions): ByteArray {
        ensureLoaded()

        val (w, h) = PAPERS[options.paperSize] ?: (210 to 297)
        val pageW = (w * RES_X / 25.4f).roundToInt()
        val pageH = (h * RES_Y / 25.4f).roundToInt()
        val paper = paperCode(options.paperSize)
        val copies = options.copies.coerceIn(1, 99)
        // 1020 是单面机，双面参数直接按关处理，别去折腾固件。
        val duplex = 1

        val out = ByteArrayOutputStream(512 * 1024)
        writePjlPrefix(out)
        writeStartDoc(out, duplex)

        for (p in 0 until source.pageCount) {
            val bmp = source.renderPage(p, pageW, pageH)
            val mono = bitmapToMono(bmp)
            bmp.recycle()
            val envelope = nativeEncodePage(mono, pageW, pageH, RES_X, RES_Y, paper, copies, duplex)
                ?: throw IllegalStateException("第 ${p + 1} 页编码失败了")
            out.write(envelope)
        }

        writeEndDoc(out)
        return out.toByteArray()
    }

    // 彩色位图按亮度阈值压成 1-bit，左边像素在最高位，行步长 (w+7)/8。
    private fun bitmapToMono(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val stride = (w + 7) / 8
        val out = ByteArray(stride * h)
        var i = 0
        for (y in 0 until h) {
            val base = y * stride
            for (x in 0 until w) {
                val c = pixels[i++]
                val lum = ((c shr 16 and 0xff) * 299 + (c shr 8 and 0xff) * 587 + (c and 0xff) * 114) / 1000
                if (lum < 128) {
                    out[base + (x shr 3)] = (out[base + (x shr 3)].toInt() or (0x80 shr (x and 7))).toByte()
                }
            }
        }
        return out
    }

    private fun paperCode(paper: String): Int = when (paper) {
        "Letter" -> 1
        "Legal" -> 5
        "A5" -> 11
        "B5" -> 13
        "A3" -> 8
        else -> 9   // 默认 A4
    }

    // --- 下面是 ZJS 容器拼接，全是大端字节序 ---

    private fun ByteArrayOutputStream.be16(v: Int) {
        write(v shr 8 and 0xff)
        write(v and 0xff)
    }

    private fun ByteArrayOutputStream.be32(v: Int) {
        write(v shr 24 and 0xff)
        write(v shr 16 and 0xff)
        write(v shr 8 and 0xff)
        write(v and 0xff)
    }

    private fun chunk(out: ByteArrayOutputStream, type: Int, items: Int, payload: Int, rsvd: Int) {
        out.be32(16 + payload)
        out.be32(type)
        out.be32(items)
        out.be16(rsvd)
        out.be16(0x5a5a)
    }

    private fun itemU32(out: ByteArrayOutputStream, item: Int, value: Int) {
        out.be32(12)
        out.be16(item)
        out.write(1)   // 类型：uint32
        out.write(0)   // param
        out.be32(value)
    }

    private fun writePjlPrefix(out: ByteArrayOutputStream) {
        val now = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        out.write("\u001b%-12345X@PJL JOB\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL SET JAMRECOVERY=OFF\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL SET DENSITY=3\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL SET ECONOMODE=OFF\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL SET RET=MEDIUM\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL INFO STATUS\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL USTATUS DEVICE = ON\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL USTATUS JOB = ON\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL USTATUS PAGE = ON\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL USTATUS TIMED = 30\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL SET JOBATTR=\"JobAttr4=".toByteArray(Charsets.US_ASCII))
        out.write(now.toByteArray(Charsets.US_ASCII))
        out.write("\"".toByteArray(Charsets.US_ASCII))
        out.write(0)
        out.write("\u001b%-12345X".toByteArray(Charsets.US_ASCII))
    }

    private fun writeStartDoc(out: ByteArrayOutputStream, duplex: Int) {
        out.write("JZJZ".toByteArray(Charsets.US_ASCII))
        chunk(out, 0, 3, 3 * 12, 0x24)
        itemU32(out, 1, 0)      // collate = 0
        itemU32(out, 2, duplex) // duplex
        itemU32(out, 0, 0)      // 页数先填 0
    }

    private fun writeEndDoc(out: ByteArrayOutputStream) {
        chunk(out, 1, 0, 0, 0)
        out.write("\u001b%-12345X@PJL EOJ\n".toByteArray(Charsets.US_ASCII))
        out.write("\u001b%-12345X".toByteArray(Charsets.US_ASCII))
    }
}