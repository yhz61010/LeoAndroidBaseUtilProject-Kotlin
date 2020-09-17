package com.ho1ho.audio.player

import android.content.Context
import android.media.*
import android.os.SystemClock
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.JsonUtil
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.audio.base.AudioCodecInfo
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Author: Michael Leo
 * Date: 20-8-20 下午5:18
 */
class AacPlayer(private val ctx: Context, private val audioDecodeInfo: AudioCodecInfo) {
    companion object {
        private const val PROFILE_AAC_LC = MediaCodecInfo.CodecProfileLevel.AACObjectLC
        private const val AUDIO_DATA_QUEUE_CAPACITY = 10
        private const val RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES = 3
        private const val AUDIO_INIT_LATENCY_IN_MS = 1200
        private const val REASSIGN_LATENCY_TIME_THRESHOLD_IN_MS: Long = 5000
        private const val AUDIO_ALLOW_LATENCY_LIMIT_IN_MS = 500
    }

    private var audioLatencyThresholdInMs = AUDIO_INIT_LATENCY_IN_MS

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private var audioManager: AudioManager? = null

    //    private var outputFormat: MediaFormat? = null
    private var frameCount = AtomicLong(0)
    private var dropFrameTimes = AtomicLong(0)
    private var playStartTimeInUs: Long = 0
    private val rcvAudioDataQueue = ArrayBlockingQueue<ByteArray>(AUDIO_DATA_QUEUE_CAPACITY)

    private var audioTrack: AudioTrack? = null
    private var audioDecoder: MediaCodec? = null

    private var csd0: ByteArray? = null

    private fun initAudioTrack(ctx: Context, audioData: AudioCodecInfo) {
        runCatching {
            audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val bufferSize = AudioTrack.getMinBufferSize(audioData.sampleRate, audioData.channelConfig, audioData.audioFormat)
            val sessionId = audioManager!!.generateAudioSessionId()
            val audioAttributesBuilder = AudioAttributes.Builder().apply {
                // Speaker
                setUsage(AudioAttributes.USAGE_MEDIA)              // AudioAttributes.USAGE_MEDIA         AudioAttributes.USAGE_VOICE_COMMUNICATION
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // AudioAttributes.CONTENT_TYPE_MUSIC   AudioAttributes.CONTENT_TYPE_SPEECH
                setLegacyStreamType(AudioManager.STREAM_MUSIC)
            }
            val audioFormat = AudioFormat.Builder().setSampleRate(audioData.sampleRate)
                .setEncoding(audioData.audioFormat)
                .setChannelMask(audioData.channelConfig)
                .build()
            audioTrack = AudioTrack(audioAttributesBuilder.build(), audioFormat, bufferSize, AudioTrack.MODE_STREAM, sessionId)

            if (AudioTrack.STATE_INITIALIZED == audioTrack!!.state) {
                LLog.w(ITAG, "Start playing audio...")
                audioTrack!!.play()
            } else {
                LLog.w(ITAG, "AudioTrack state is not STATE_INITIALIZED")
            }
        }.onFailure { LLog.e(ITAG, "initAudioTrack error msg=${it.message}") }
    }

    fun useSpeaker(ctx: Context, on: Boolean) {
        LLog.w(ITAG, "useSpeaker=$on")
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (on) {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
        } else {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
        }
    }

    private fun initAudioDecoder(csd0: ByteArray) {
        runCatching {
            LLog.i(ITAG, "initAudioDecoder: ${JsonUtil.toJsonString(audioDecodeInfo)}")
            audioDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val mediaFormat =
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioDecodeInfo.sampleRate, audioDecodeInfo.channelCount)
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, PROFILE_AAC_LC)
            mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1)

            // ByteBuffer key
            // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
            // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
            // Convert them to binary value: 0b10, 0b100, 0b1
            // According to AAC required, convert theirs values to binary bits:
            // 00010 0100 0001 000
            // The corresponding hex value：
            // 0001 0010 0000 1000
            // So the csd_0 value is 0x12,0x08
            // https://developer.android.com/reference/android/media/MediaCodec
            // AAC CSD: Decoder-specific information from ESDS

            // ByteBuffer key
            // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
            // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
            // Convert them to binary value: 0b10, 0b100, 0b1
            // According to AAC required, convert theirs values to binary bits:
            // 00010 0100 0001 000
            // The corresponding hex value：
            // 0001 0010 0000 1000
            // So the csd_0 value is 0x12,0x08
            // https://developer.android.com/reference/android/media/MediaCodec
            // AAC CSD: Decoder-specific information from ESDS
            val csd0BB = ByteBuffer.wrap(csd0)
            // Set ADTS decoder information.
            mediaFormat.setByteBuffer("csd-0", csd0BB)
            audioDecoder!!.configure(mediaFormat, null, null, 0)
//            outputFormat = audioDecoder?.outputFormat // option B
//            audioDecoder?.setCallback(mediaCodecCallback)
            audioDecoder?.start()
            ioScope.launch {
                runCatching {
                    while (true) {
                        ensureActive()
                        val audioData = rcvAudioDataQueue.poll()
//                        if (frameCount.get() % 30 == 0L) {
                        LLog.i(ITAG, "Play AAC[${audioData?.size}]")
//                        }
                        if (audioData != null && audioData.isNotEmpty()) {
                            decodeAndPlay(audioData)
                        }
                        delay(10)
                    }
                }.onFailure { it.printStackTrace() }
            }
        }.onFailure { LLog.e(ITAG, "initAudioDecoder error msg=${it.message}") }
    }

    /**
     * If I use asynchronous MediaCodec, most of time in my phone(HuaWei Honor V20), it will not play sound due to MediaCodec state error.
     */
    private fun decodeAndPlay(audioData: ByteArray) {
        try {
            val bufferInfo = MediaCodec.BufferInfo()

            // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
            val inputIndex: Int = audioDecoder?.dequeueInputBuffer(0) ?: -1
            if (inputIndex < 0) {
                return
            }
            audioDecoder?.getInputBuffer(inputIndex)?.run {
                // Clear exist data.
                clear()
                // Put pcm audio data to encoder.
                put(audioData)
            }
            val pts = computePresentationTimeUs(frameCount.incrementAndGet())
            audioDecoder?.queueInputBuffer(inputIndex, 0, audioData.size, pts, 0)

            // Start decoding and get output index
            var outputIndex: Int = audioDecoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            val chunkPCM = ByteArray(bufferInfo.size)
            // Get decoded data in bytes
            while (outputIndex >= 0) {
                audioDecoder?.getOutputBuffer(outputIndex)?.run { get(chunkPCM) }
                // Must clear decoded data before next loop. Otherwise, you will get the same data while looping.
                if (chunkPCM.isNotEmpty()) {
                    if (audioTrack == null || AudioTrack.STATE_UNINITIALIZED == audioTrack?.state) return
                    if (AudioTrack.PLAYSTATE_PLAYING == audioTrack?.playState) {
                        LLog.i(ITAG, "Play PCM[${chunkPCM.size}]")
                        // Play decoded audio data in PCM
                        audioTrack?.write(chunkPCM, 0, chunkPCM.size)
                    }
                }
                audioDecoder?.releaseOutputBuffer(outputIndex, false)
                // Get data again.
                outputIndex = audioDecoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            }
        } catch (e: Exception) {
            LLog.e(ITAG, "You can ignore this message safely. decodeAndPlay error")
        }
    }

//    private val mediaCodecCallback = object : MediaCodec.Callback() {
//        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
//            try {
//                val inputBuffer = codec.getInputBuffer(inputBufferId)
//                // fill inputBuffer with valid data
//                inputBuffer?.clear()
//                val data = rcvAudioDataQueue.poll()?.also {
//                    inputBuffer?.put(it)
//                }
//                val dataSize = data?.size ?: 0
//                val pts = computePresentationTimeUs(frameCount.incrementAndGet())
////                if (BuildConfig.DEBUG) {
////                    LLog.d(ITAG, "Data len=$dataSize\t pts=$pts")
////                }
//                codec.queueInputBuffer(inputBufferId, 0, dataSize, pts, 0)
//            } catch (e: Exception) {
//                LLog.e(ITAG, "Audio Player onInputBufferAvailable error. msg=${e.message}")
//            }
//        }
//
//        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
//            try {
//                val outputBuffer = codec.getOutputBuffer(outputBufferId)
//                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
//                // bufferFormat is equivalent to member variable outputFormat
//                // outputBuffer is ready to be processed or rendered.
//                outputBuffer?.let {
//                    val decodedData = ByteArray(info.size)
//                    it.get(decodedData)
////                LLog.w(ITAG, "PCM[${decodedData.size}]")
//                    when (info.flags) {
//                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
//                            LLog.w(ITAG, "Found CSD0 frame: ${JsonUtil.toJsonString(decodedData)}")
//                        }
//                        MediaCodec.BUFFER_FLAG_END_OF_STREAM -> Unit
//                        MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> Unit
//                        else -> Unit
//                    }
//                    if (decodedData.isNotEmpty()) {
//                        // Play decoded audio data in PCM
//                        audioTrack?.write(decodedData, 0, decodedData.size)
//                    }
//                }
//                codec.releaseOutputBuffer(outputBufferId, false)
//            } catch (e: Exception) {
//                LLog.e(ITAG, "Audio Player onOutputBufferAvailable error. msg=${e.message}")
//            }
//        }
//
//        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
//            LLog.w(ITAG, "onOutputFormatChanged format=$format")
//            // Subsequent data will conform to new format.
//            // Can ignore if using getOutputFormat(outputBufferId)
//            outputFormat = format // option B
//        }
//
//        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
//            e.printStackTrace()
//            LLog.e(ITAG, "onError e=${e.message}", e)
//        }
//    }

    fun startPlayingStream(audioData: ByteArray, f: () -> Unit) {
        // We have a better way to check CSD0
        if (audioData.size < 10) {
            runCatching {
                synchronized(this) {
                    audioDecoder?.run {
                        LLog.w(ITAG, "Found exist AAC Audio Decoder. Release it first")
                        stop()
                        release()
                    }
                    audioTrack?.run {
                        LLog.w(ITAG, "Found exist AudioTrack. Release it first")
                        stop()
                        release()
                    }
                    frameCount.set(0)
                    csd0 = byteArrayOf(audioData[audioData.size - 2], audioData[audioData.size - 1])
                    LLog.w(ITAG, "Audio csd0=${JsonUtil.toHexadecimalString(csd0)}")
                    initAudioDecoder(csd0!!)
                    initAudioTrack(ctx, audioDecodeInfo)
                    playStartTimeInUs = SystemClock.elapsedRealtimeNanos() / 1000
                    ioScope.launch {
                        delay(REASSIGN_LATENCY_TIME_THRESHOLD_IN_MS)
                        audioLatencyThresholdInMs = AUDIO_ALLOW_LATENCY_LIMIT_IN_MS
                        LLog.w(ITAG, "Change latency limit to $AUDIO_ALLOW_LATENCY_LIMIT_IN_MS")
                    }
                    LLog.w(ITAG, "Play audio at: $playStartTimeInUs")
                }
            }.onFailure { LLog.e(ITAG, "startPlayingStream error. msg=${it.message}") }
            return
        }
        if (csd0 == null) {
            LLog.e(ITAG, "csd0 is null. Can not play!")
            return
        }
        val latencyInMs = (SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000 - getAudioTimeUs() / 1000
//        LLog.d(
//            ITAG,
//            "st=$playStartTimeInUs\t cal=${(SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000}\t play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
//        )
        if (rcvAudioDataQueue.size >= AUDIO_DATA_QUEUE_CAPACITY || abs(latencyInMs) > audioLatencyThresholdInMs) {
            dropFrameTimes.incrementAndGet()
            LLog.w(
                ITAG,
                "Drop[${dropFrameTimes.get()}]|full[${rcvAudioDataQueue.size}] latency[$latencyInMs] play=${getAudioTimeUs() / 1000}"
            )
            rcvAudioDataQueue.clear()
            frameCount.set(0)
            runCatching { audioDecoder?.flush() }.getOrNull()
            runCatching { audioTrack?.pause() }.getOrNull()
            runCatching { audioTrack?.flush() }.getOrNull()
            runCatching { audioTrack?.play() }.getOrNull()
            if (dropFrameTimes.get() >= RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES) {
                // If drop frame times exceeds RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES-1 times, do need to do sync again.
                dropFrameTimes.set(0)
                f.invoke()
            }
            playStartTimeInUs = SystemClock.elapsedRealtimeNanos() / 1000
        }
        if (frameCount.get() % 50 == 0L) {
            LLog.i(ITAG, "AU[${audioData.size}][$latencyInMs]")
        }
        rcvAudioDataQueue.offer(audioData)
    }

    fun stopPlaying() {
        LLog.w(ITAG, "Stop playing audio")
        runCatching {
            ioScope.cancel()
            rcvAudioDataQueue.clear()
            frameCount.set(0)
            dropFrameTimes.set(0)
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        }.onFailure {
            LLog.e(ITAG, "audioTrack stop or release error. msg=${it.message}")
        }.also {
            audioTrack = null
        }

        LLog.w(ITAG, "Releasing AudioDecoder...")
        runCatching {
            // These are the magic lines for Samsung phone. DO NOT try to remove or refactor me.
            audioDecoder?.setCallback(null)
            audioDecoder?.release()
        }.onFailure {
            it.printStackTrace()
            LLog.e(ITAG, "audioDecoder() release1 error. msg=${it.message}")
        }.also {
            audioDecoder = null
            csd0 = null
        }

        LLog.w(ITAG, "stopPlaying() done")
    }

    fun getPlayState() = audioTrack?.playState ?: AudioTrack.PLAYSTATE_STOPPED

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / audioDecodeInfo.sampleRate

    private fun getAudioTimeUs(): Long = runCatching {
        val numFramesPlayed: Int = audioTrack?.playbackHeadPosition ?: 0
        numFramesPlayed * 1_000_000L / audioDecodeInfo.sampleRate
    }.getOrDefault(0L)
}