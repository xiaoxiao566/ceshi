package com.ceshi.printtool.print

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HP LaserJet 1020 固件处理。
 *
 * 该机型是「主机型（GDI）」打印机，ROM 极小、无内置固件，每次上电/连接都必须先
 * 上传固件 `sihp1020.dl` 才能通信（与 foo2zjs 的做法一致）：
 *   1. 先从应用 assets/firmware/ 或网络获取固件；
 *   2. 通过 USB bulk OUT 端点逐块写入；
 *   3. 打印机收到固件后自行复位并重新枚举，之后才可接收打印数据。
 */
object Hp1020Firmware {

    const val FIRMWARE_NAME = "sihp1020.dl"
    private const val ASSET_PATH = "firmware/$FIRMWARE_NAME"

    /**
     * 固件下载源（按顺序回退）。
     * HP 官方源已失效，foo2zjs `./getweb 1020` 的旧 vuji/flickr 地址也已不可用，
     * 这里使用经过验证的社区镜像（HTTP 200，约 126KB）。
     */
    private val FIRMWARE_URLS = listOf(
        "https://raw.githubusercontent.com/FZJ-SDU/hp-laserjet-1020-plus-macos-driver/main/sihp1020.dl",
        "https://raw.githubusercontent.com/koenkooi/foo2zjs/master/firmware/sihp1020.dl",
        "http://foo2zjs.rkkda.com/firmware/sihp1020.dl"
    )

    fun firmwareFile(context: Context): File = File(context.filesDir, FIRMWARE_NAME)

    /** 固件是否已就绪 */
    fun isReady(context: Context): Boolean = firmwareFile(context).length() > 0

    /** 获取固件本地文件（优先内置 assets，其次联网下载）；失败返回 null */
    fun ensure(context: Context): File? {
        val file = firmwareFile(context)
        if (file.length() > 0) return file
        try {
            context.assets.open(ASSET_PATH).use { ins ->
                FileOutputStream(file).use { out -> ins.copyTo(out) }
            }
            if (file.length() > 0) return file
        } catch (_: Exception) {
            // 无内置固件，尝试下载
        }
        return try {
            download(FIRMWARE_URLS, file)
        } catch (e: Exception) {
            null
        }
    }

    private fun download(urls: List<String>, dest: File): File? {
        for (url in urls) {
            try {
                downloadOne(url, dest)?.let { return it }
            } catch (_: Exception) {
                // 尝试下一个镜像
            }
        }
        return null
    }

    private fun downloadOne(url: String, dest: File): File? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        return try {
            if (conn.responseCode != 200) return null
            conn.inputStream.use { ins ->
                FileOutputStream(dest).use { out -> ins.copyTo(out) }
            }
            if (dest.length() > 0) dest else null
        } finally {
            conn.disconnect()
        }
    }

    /** 逐块上传固件，成功返回 true */
    fun upload(connection: UsbDeviceConnection, out: UsbEndpoint, firmware: File): Boolean {
        val buf = ByteArray(16384)
        return try {
            firmware.inputStream().use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    val sent = connection.bulkTransfer(out, buf, n, 3000)
                    if (sent < 0) return false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}