package com.ceshi.printtool.print

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// 1020 没内置固件，每次通电都得先把 sihp1020.dl 塞进去才能用，跟 foo2zjs 一个套路。
object Hp1020Firmware {

    const val FIRMWARE_NAME = "sihp1020.dl"
    private const val ASSET_PATH = "firmware/$FIRMWARE_NAME"

    // 老地址基本都挂了，留几个镜像挨个试。
    private val FIRMWARE_URLS = listOf(
        "https://raw.githubusercontent.com/FZJ-SDU/hp-laserjet-1020-plus-macos-driver/main/sihp1020.dl",
        "https://raw.githubusercontent.com/koenkooi/foo2zjs/master/firmware/sihp1020.dl",
        "http://foo2zjs.rkkda.com/firmware/sihp1020.dl"
    )

    fun firmwareFile(context: Context): File = File(context.filesDir, FIRMWARE_NAME)

    fun isReady(context: Context): Boolean = firmwareFile(context).length() > 0

    // 先试内置 assets，没有就联网下，拿不到就返回 null
    fun ensure(context: Context): File? {
        val file = firmwareFile(context)
        if (file.length() > 0) return file
        try {
            context.assets.open(ASSET_PATH).use { ins ->
                FileOutputStream(file).use { out -> ins.copyTo(out) }
            }
            if (file.length() > 0) return file
        } catch (_: Exception) {
            // 没内置，走下载
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
                // 换个镜像再试
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

    // 分块写进去
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