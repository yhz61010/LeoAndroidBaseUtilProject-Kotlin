package com.ho1ho.audio.base

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 20-8-13 下午7:21
 *
 * Example:
 * ```
 * sampleRate: 44100Hz
 * bitrate: 32000bps
 * channelConfig:
 *              AudioFormat.CHANNEL_IN_MONO(16=0x10)
 *              AudioFormat.CHANNEL_OUT_STEREO(12=0xc)
 *              AudioFormat.CHANNEL_OUT_MONO (0x4)
 * channelCount: 1
 * audioFormat(bit depth):
 *              ENCODING_PCM_16BIT.ENCODING_PCM_16BIT(0x2)
 *              ENCODING_PCM_16BIT.ENCODING_PCM_8BIT(0x3)
 *              ENCODING_PCM_16BIT.ENCODING_PCM_FLOAT(0x4)
 * ```
 */
@Keep
data class AudioCodecInfo(val sampleRate: Int, val bitrate: Int, val channelConfig: Int, val channelCount: Int, val audioFormat: Int)