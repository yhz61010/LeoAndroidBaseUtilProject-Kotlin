package com.leovp.leoandroidbaseutil.basic_components.examples.camera2

import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.util.Size
import android.view.SurfaceHolder
import android.view.View
import com.leovp.androidbase.exts.android.getPreviewOutputSize
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.media.CameraUtil
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.camera2live.Camera2ComponentHelper
import com.leovp.camera2live.view.BaseCamera2Fragment

/**
 * Author: Michael Leo
 * Date: 20-6-29 上午9:50
 */
class Camera2LiveFragment : BaseCamera2Fragment() {
    companion object {
        private val TAG = Camera2LiveFragment::class.java.simpleName
        private val DESIGNED_CAMERA_SIZE = Camera2ComponentHelper.CAMERA_SIZE_EXTRA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableRecordFeature = true
        enableTakePhotoFeature = true

        LogContext.log.w(
            TAG, "Supported image format for avc encoder: ${
                CodecUtil.getSupportedColorFormatForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC).sorted().joinToString(",")
            }"
        )

        CodecUtil.getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC)?.forEach { it ->
            LogContext.log.w(TAG, "Supported profile profile/level for avc encoder: profile=${it.profile} level=${it.level}")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraView.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
//                camera2Helper.encoderType = if (
//                    CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
//                    || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.Exynos.AVC.Encoder")
//                    || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.MTK.VIDEO.ENCODER.AVC")
//                ) DataProcessFactory.ENCODER_TYPE_YUV_ORIGINAL
//                else DataProcessFactory.ENCODER_TYPE_NORMAL
//                camera2Helper.encoderType = DataProcessFactory.ENCODER_TYPE_YUV420SP

                CodecUtil.getEncoderListByMimeType(MediaFormat.MIMETYPE_VIDEO_AVC).forEach { LogContext.log.i(TAG, "Encoder: ${it.name}") }

                // Selects appropriate preview size and configures camera surface
                val previewSize = getPreviewOutputSize(
                    Size(DESIGNED_CAMERA_SIZE.width, DESIGNED_CAMERA_SIZE.height)/*cameraView.display*/,
                    camera2Helper.characteristics,
                    SurfaceHolder::class.java
                )
                LogContext.log.d(TAG, "CameraSurfaceView size: ${cameraView.width}x${cameraView.height}")
                LogContext.log.d(TAG, "Selected preview size: $previewSize")
                cameraView.setDimension(previewSize.width, previewSize.height)
                // To ensure that size is set, initialize camera in the view's thread
                view.post {
                    runCatching {
                        camera2Helper.initializeCamera(previewSize.width, previewSize.height)
                    }.getOrElse {
                        LogContext.log.e(TAG, "=====> Finally openCamera error <=====")
                        ToastUtil.showErrorToast("Initialized camera error. Please try again later.")
                    }
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }

    override suspend fun getCapturingImage(result: Camera2ComponentHelper.CombinedCaptureResult) {

        // Save the result to disk
        val output = camera2Helper.saveResult(result)
        LogContext.log.d(TAG, "Image saved: ${output.absolutePath}")

        // If the result is a JPEG file, update EXIF metadata with orientation info
//            if (output.extension == "jpg") {
//                val exif = ExifInterface(output.absolutePath)
//                exif.setAttribute(ExifInterface.TAG_ORIENTATION, result.orientation.toString())
//                exif.saveAttributes()
//                LogContext.log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
//            }

        // Display the photo taken to user
//                    lifecycleScope.launch(Dispatchers.Main) {
//                        navController.navigate(
//                            CameraFragmentDirections
//                                .actionCameraToJpegViewer(output.absolutePath)
//                                .setOrientation(result.orientation)
//                                .setDepth(
//                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
//                                            result.format == ImageFormat.DEPTH_JPEG
//                                )
//                        )
//                    }
    }

    override suspend fun onRecordButtonClick() {
        LogContext.log.w(TAG, "onRecordButtonClick")
        // CAMERA_SIZE_NORMAL & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=5
        // BITRATE_MODE_CQ: 348.399kB/s
        // BITRATE_MODE_CBR: 85.875kB/s
        // BITRATE_MODE_VBR: 84.929kB/s
        // CAMERA_SIZE_HIGH & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=3
        // BITRATE_MODE_CBR: 113.630kB/s
        val camera2ComponentBuilder = camera2Helper.Builder(DESIGNED_CAMERA_SIZE.width, DESIGNED_CAMERA_SIZE.height)
//        camera2ComponentBuilder.previewInFullscreen = true
        camera2ComponentBuilder.quality = Camera2ComponentHelper.BITRATE_LOW
        // On Nexus6 Camera Fps should be CAMERA_FPS_VERY_HIGH - Range(30, 30)
        camera2ComponentBuilder.cameraFps = Camera2ComponentHelper.CAMERA_FPS_LOW
        camera2ComponentBuilder.videoFps = Camera2ComponentHelper.VIDEO_FPS_FREQUENCY_NORMAL
        camera2ComponentBuilder.iFrameInterval = 5
        camera2ComponentBuilder.bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        camera2ComponentBuilder.build()
        camera2Helper.outputH264ForDebug = true
        camera2Helper.setEncodeListener(object : Camera2ComponentHelper.EncodeDataUpdateListener {
            override fun onUpdate(h264Data: ByteArray) {
                LogContext.log.d(TAG, "Get encoded video data length=${h264Data.size}")
            }
        })
        camera2Helper.setLensSwitchListener(object : Camera2ComponentHelper.LensSwitchListener {
            override fun onSwitch(lensFacing: Int) {
                LogContext.log.w(TAG, "lensFacing=$lensFacing")
                if (CameraMetadata.LENS_FACING_FRONT == lensFacing) {
                    switchFlashBtn.isChecked = false
                    switchFlashBtn.visibility = View.GONE
                } else {
                    switchFlashBtn.visibility = View.VISIBLE
                }
                previousLensFacing = lensFacing
            }
        })

        camera2Helper.initDebugOutput()
    }

    override suspend fun onStopRecordButtonClick() {
        camera2Helper.closeDebugOutput()
    }

    override fun onOpenGallery() {
        CameraUtil.openGallery(requireActivity(), false)
    }
}