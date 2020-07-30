package com.ho1ho.androidbase.exts

/**
 * Author: Michael Leo
 * Date: 20-3-18 下午2:17
 */
private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

fun Byte.toBytes(): ByteArray = byteArrayOf(this)

/**
 * Force convert int value as byte array.
 */
fun Int.asByteAndForceToBytes(): ByteArray = this.toByte().toBytes()

fun ByteArray.readByte(index: Int = 0): Byte = (this[index].toInt() and 0xFF).toByte()

fun ByteArray.readShort(index: Int = 0): Short = (((this[index + 0].toInt() shl 8) or (this[index + 1].toInt() and 0xFF)).toShort())
fun ByteArray.readShortLE(index: Int = 0): Short = (((this[index + 1].toInt() shl 8) or (this[index + 0].toInt() and 0xFF)).toShort())

fun ByteArray.readInt(index: Int = 0): Int = this[3 + index].toInt() and 0xFF or (
        this[2 + index].toInt() and 0xFF shl 8) or (
        this[1 + index].toInt() and 0xFF shl 16) or (
        this[0 + index].toInt() and 0xFF shl 24)

fun ByteArray.readIntLE(index: Int = 0): Int = this[index].toInt() and 0xFF or (
        this[index + 1].toInt() and 0xFF shl 8) or (
        this[index + 2].toInt() and 0xFF shl 16) or (
        this[index + 3].toInt() and 0xFF shl 24)


fun bytes2Long(b: ByteArray): Long {
    return b[7].toLong() and 0xFF or (
            b[6].toLong() and 0xFF shl 8) or (
            b[5].toLong() and 0xFF shl 16) or (
            b[4].toLong() and 0xFF shl 24) or (
            b[3].toLong() and 0xFF shl 32) or (
            b[2].toLong() and 0xFF shl 40) or (
            b[1].toLong() and 0xFF shl 48) or (
            b[0].toLong() and 0xFF shl 56)
}


fun ByteArray.readLong(): Long {
    var result: Long = 0
    for (i in 0 until Long.SIZE_BYTES) result = result or ((this[i].toLong() and 0xFF) shl (Long.SIZE_BYTES - 1 - i) * 8)
    return result
}

fun ByteArray.readLongLE(): Long {
    var result: Long = 0
    for (i in 0 until Long.SIZE_BYTES) result = result or ((this[i].toLong() and 0xFF) shl (i * 8))
    return result
}

// =============================================

fun Short.toBytes(): ByteArray =
    ByteArray(Short.SIZE_BYTES).also { for (i in it.indices) it[i] = (this.toInt() ushr ((Short.SIZE_BYTES - 1 - i) * 8) and 0xFF).toByte() }

fun Short.toBytesLE(): ByteArray = ByteArray(Short.SIZE_BYTES).also { for (i in it.indices) it[i] = ((this.toInt() ushr i * 8) and 0xFF).toByte() }

fun Int.toBytes(): ByteArray =
    ByteArray(Int.SIZE_BYTES).also { for (i in it.indices) it[i] = (this ushr ((Int.SIZE_BYTES - 1 - i) * 8) and 0xFF).toByte() }

fun Int.toBytesLE(): ByteArray = ByteArray(Int.SIZE_BYTES).also { for (i in it.indices) it[i] = ((this ushr (i * 8)) and 0xFF).toByte() }

fun Long.toBytes(): ByteArray =
    ByteArray(Long.SIZE_BYTES).also { for (i in it.indices) it[i] = (this ushr ((Long.SIZE_BYTES - 1 - i) * 8) and 0xFF).toByte() }

fun Long.toBytesLE(): ByteArray = ByteArray(Long.SIZE_BYTES).also { for (i in it.indices) it[i] = ((this ushr (i * 8)) and 0xFF).toByte() }

// ==============================================

fun Byte.toHexString() = let { "%02X".format(it) }
fun ByteArray.toAsciiString(delimiter: CharSequence = ",") = map { it.toChar() }.joinToString(delimiter)

/**
 * Attention.
 * This method is little bit slow if you want to use in it loop.
 */
//fun ByteArray.toHexString(delimiter: CharSequence = " ") = joinToString(delimiter) { "%02X".format(it) }

fun ByteArray.toHexString(delimiter: CharSequence = ","): String {
    val result = StringBuilder()
    forEach {
        val octet = it.toInt()
        val highBit = octet and 0x0F
        val lowBit = (octet and 0xF0).ushr(4)
        result.append(HEX_CHARS[highBit])
        result.append(HEX_CHARS[lowBit])
        if (delimiter.isNotEmpty()) result.append(delimiter)
    }
    if (delimiter.isNotEmpty()) result.deleteCharAt(result.length - 1)
    return result.toString()
}

fun ByteArray.toHexStringLE(delimiter: CharSequence = ","): String {
    val result = StringBuilder()
    forEach {
        val octet = it.toInt()
        val highBit = (octet and 0xF0).ushr(4)
        val lowBit = octet and 0x0F
        result.append(HEX_CHARS[highBit])
        result.append(HEX_CHARS[lowBit])
        if (delimiter.isNotEmpty()) result.append(delimiter)
    }
    if (delimiter.isNotEmpty()) result.deleteCharAt(result.length - 1)
    return result.toString()
}