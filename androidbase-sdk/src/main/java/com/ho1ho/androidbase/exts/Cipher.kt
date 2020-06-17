package com.ho1ho.androidbase.exts

import com.ho1ho.androidbase.utils.cipher.MD5Util
import java.util.*

/**
 * Author: Michael Leo
 * Date: 20-6-17 上午10:28
 */
fun String.toMd5(allUpperCase: Boolean = false): String {
    return MD5Util.encrypt(this).let { if (allUpperCase) it.toUpperCase(Locale.getDefault()) else it }
}