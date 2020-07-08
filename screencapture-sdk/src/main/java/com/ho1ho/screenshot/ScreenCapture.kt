package com.ho1ho.screenshot

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.media.MediaCodecInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.screenshot.base.*

/**
 * Author: Michael Leo
 * Date: 20-3-12 下午7:31
 */
object ScreenCapture {

    private const val TAG = "ScrCap"

    const val SCREEN_CAPTURE_TYPE_IMAGE = 1
    const val SCREEN_CAPTURE_TYPE_MEDIACODEC = 2

    @Suppress("unused")
    const val SCREEN_CAPTURE_TYPE_X264 = 3

    private const val REQUEST_CODE_SCREEN_CAPTURE = 0x123

    const val SCREEN_CAPTURE_RESULT_GRANT = 1
    const val SCREEN_CAPTURE_RESULT_DENY = 2

    @Suppress("WeakerAccess")
    const val SCREEN_CAPTURE_RESULT_IGNORE = 3

    interface ScreenCaptureListener {
        fun requestResult(result: Int, resultCode: Int, data: Intent?)
    }

    class Builder(
        private val width: Int,
        private val height: Int,
        private val dpi: Int,
        private val mediaProjection: MediaProjection,
        private val captureType: Int,
        private val screenDataListener: ScreenDataListener
    ) {
        // Common setting
        private var fps = 20F

        // H264 setting
        private var bitrate = width * height
        private var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        private var keyFrameRate = 20
        private var iFrameInterval = 1

        // Screenshot setting
        private var sampleSize = 1

        // ==================================================
        // ===== For H264
        // ==================================================
        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setKeyFrameRate(keyFrameRate: Int) = apply { this.keyFrameRate = keyFrameRate }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }

        // ==================================================
        // ===== For Image
        // ==================================================
        /**
         * Only used in `ScreenCapture.SCREEN_CAPTURE_TYPE_IMAGE` mode
         */
        @Suppress("unused")
        fun setSampleSize(sample: Int) = apply { this.sampleSize = sample }

        fun build(): ScreenProcessor {
            LLog.i(
                TAG,
                "width=$width height=$height dpi=$dpi captureType=$captureType fps=$fps bitrate=$bitrate bitrateMode=$bitrateMode keyFrameRate=$keyFrameRate iFrameInterval=$iFrameInterval sampleSize=$sampleSize"
            )
            return when (captureType) {
                SCREEN_CAPTURE_TYPE_IMAGE ->
                    ScreenshotStrategy.Builder(width, height, dpi, mediaProjection, screenDataListener)
                        .setFps(fps)
                        .setSampleSize(sampleSize)
                        .build()
                SCREEN_CAPTURE_TYPE_MEDIACODEC ->
                    ScreenRecordMediaCodecStrategy.Builder(width, height, dpi, mediaProjection, screenDataListener)
                        .setFps(fps)
                        .setBitrate(bitrate)
                        .setBitrateMode(bitrateMode)
                        .setKeyFrameRate(keyFrameRate)
                        .setIFrameInterval(iFrameInterval)
                        .build()
                else ->
                    ScreenRecordX264Strategy.Builder(width, height, dpi, mediaProjection, screenDataListener)
                        .setFps(fps)
                        .setBitrate(bitrate)
                        .build()
            }
        }
    }

    fun requestPermission(act: Activity) {
        val mediaProjectionManager = act.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        act.startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?, listener: ScreenCaptureListener) {
        if (requestCode != REQUEST_CODE_SCREEN_CAPTURE) {
            listener.requestResult(SCREEN_CAPTURE_RESULT_IGNORE, resultCode, data)
            return
        }
        if (resultCode != RESULT_OK) {
            listener.requestResult(SCREEN_CAPTURE_RESULT_DENY, resultCode, data)
            return
        }

        listener.requestResult(SCREEN_CAPTURE_RESULT_GRANT, resultCode, data)
    }
}