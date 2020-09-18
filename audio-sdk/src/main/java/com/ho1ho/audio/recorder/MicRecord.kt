package com.ho1ho.audio.recorder

import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.toJsonString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.audio.base.AudioCodecInfo
import kotlinx.coroutines.*

/**
 * Author: Michael Leo
 * Date: 20-8-20 下午3:51
 */
class MicRecorder(encoderInfo: AudioCodecInfo, val callback: RecordCallback) {
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private var audioRecord: AudioRecord

    private var bufferSizeInBytes = 0

    init {
        LLog.w(ITAG, "recordAudio=${encoderInfo.toJsonString()}")
        bufferSizeInBytes = AudioRecord.getMinBufferSize(encoderInfo.sampleRate, encoderInfo.channelConfig, encoderInfo.audioFormat)

        audioRecord = AudioRecord(
            // MediaRecorder.AudioSource.MIC
            // MediaRecorder.AudioSource.VOICE_COMMUNICATION
            // MediaRecorder.AudioSource.CAMCORDER
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            encoderInfo.sampleRate,
            encoderInfo.channelConfig,
            encoderInfo.audioFormat,
            bufferSizeInBytes
        )
        // https://blog.csdn.net/lavender1626/article/details/80394253
        initAdvancedFeatures()
    }

    fun startRecord() {
        LLog.w(ITAG, "Do startRecord()")
        audioRecord.startRecording()
        ioScope.launch {
            runCatching {
                val pcmData = ByteArray(bufferSizeInBytes)
                var st: Long
                var cost: Long
                var recordSize: Int
                while (true) {
                    ensureActive()
                    st = SystemClock.elapsedRealtime()
                    recordSize = audioRecord.read(pcmData, 0, pcmData.size)
                    cost = SystemClock.elapsedRealtime() - st
                    LLog.i(ITAG, "Record[$recordSize] cost $cost ms.")
                    // If you want to reduce latency when transfer real-time audio stream,
                    // please drop the first generated audio.
                    // It will cost almost 200ms due to preparing the first audio data.
                    // For the second and subsequent audio data, it will only cost 40ms-.
//                    if (cost > 100) {
//                        LLog.w(ITAG, "Drop the generate audio data which cost over 100 ms.")
//                        continue
//                    }
                    callback.onRecording(pcmData)
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    private fun initAdvancedFeatures() {
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(audioRecord.audioSessionId)?.run {
                LLog.w(ITAG, "Enable AcousticEchoCanceler")
                enabled = true
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(audioRecord.audioSessionId)?.run {
                LLog.w(ITAG, "Enable AutomaticGainControl")
                enabled = true
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioRecord.audioSessionId)?.run {
                LLog.w(ITAG, "Enable NoiseSuppressor")
                enabled = true
            }
        }
    }

    fun stopRecord() {
        LLog.w(ITAG, "Stop recording audio")
        var stopResult = true
        runCatching {
            LLog.w(ITAG, "Stopping recording...")
            audioRecord.stop()
        }.onFailure { it.printStackTrace(); stopResult = false }
        runCatching {
            LLog.w(ITAG, "Releasing recording...")
            audioRecord.release()
        }.onFailure { it.printStackTrace(); stopResult = false }
        ioScope.cancel()
        callback.onStop(stopResult)
    }

    fun getRecordingState() = audioRecord.recordingState

    interface RecordCallback {
        fun onRecording(pcmData: ByteArray)
        fun onStop(stopResult: Boolean)
    }
}