package com.ceshi.printtool.print

import android.graphics.Bitmap

/**
 * ZjStream（ZJS）栅格编码器 —— 占位实现，尚未完成。
 *
 * HP LaserJet 1020 是「主机型」打印机，固件上传之后，页面还必须编码成
 * Zenographics ZjStream（ZJS）压缩数据才能打印（这是 foo2zjs 的 zjs*.c
 * 所做的核心工作，属于专有栅格压缩，无公开的移动端现成库）。
 *
 * 完整实现方向：
 *   1. 以 NDK/JNI 移植 foo2zjs 的 zjs*.c（zjsmono/zjscompress）；
 *   2. 或通过 CUPS/IPP 桥接：让 1020 挂在树莓派/PC 的 CUPS 上，Android 走 IPP 无线打印。
 *
 * 本类仅提供接口占位，接入真实编码器后即可在 OtgPrintManager 中使用。
 */
object ZjsEncoder {

    /** 是否拥有可用的 ZJS 编码实现 */
    const val IMPLEMENTED = false

    fun encodeMonochrome(bitmap: Bitmap, dpi: Int): ByteArray {
        throw UnsupportedOperationException(
            "HP 1020 的 ZjStream(ZJS) 栅格编码尚未接入。当前版本已完成固件上传与设备通信，" +
                "请改用无线打印（IPP/Mopria），或将 1020 接入 CUPS 后经 IPP 打印。"
        )
    }
}