package com.ho1ho.leoandroidbaseutil.basic_components.examples.camera2

import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecInfo
import android.os.Bundle
import android.util.Size
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.getPreviewOutputSize
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.camera2live.Camera2ComponentHelper
import com.ho1ho.camera2live.base.DataProcessFactory
import com.ho1ho.leoandroidbaseutil.R
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import kotlinx.android.synthetic.main.activity_camera2_without_preview.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Camera2WithoutPreviewActivity : AppCompatActivity() {
    companion object {
        private val DESIGNED_CAMERA_SIZE = Camera2ComponentHelper.CAMERA_SIZE_FOR_VIDEO_CHAT_NORMAL
    }

    private lateinit var camera2Helper: Camera2ComponentHelper
    private var previousLensFacing = CameraMetadata.LENS_FACING_BACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera2_without_preview)

        AndPermission.with(this@Camera2WithoutPreviewActivity)
            .runtime()
            .permission(Permission.CAMERA)
            .onGranted {
                Toast.makeText(this@Camera2WithoutPreviewActivity, "Grant camera permission", Toast.LENGTH_SHORT).show()
            }
            .onDenied { Toast.makeText(this@Camera2WithoutPreviewActivity, "Deny camera permission", Toast.LENGTH_SHORT).show() }
            .start()

        camera2Helper = Camera2ComponentHelper(this, CameraMetadata.LENS_FACING_BACK).apply {
            enableRecordFeature = false
            enableTakePhotoFeature = false
            enableGallery = false
            setLensSwitchListener(object : Camera2ComponentHelper.LensSwitchListener {
                override fun onSwitch(lensFacing: Int) {
                    LLog.w(ITAG, "lensFacing=$lensFacing")
                    if (CameraMetadata.LENS_FACING_FRONT == lensFacing) {
                        LLog.w(ITAG, "Front lens")
                    } else {
                        LLog.w(ITAG, "Back lens")
                    }
                    previousLensFacing = lensFacing
                }
            })

//            encoderType = if (
//                CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
//                || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.Exynos.AVC.Encoder")
//                || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.MTK.VIDEO.ENCODER.AVC")
//            ) DataProcessFactory.ENCODER_TYPE_YUV_ORIGINAL
//            else DataProcessFactory.ENCODER_TYPE_NORMAL
            encoderType = DataProcessFactory.ENCODER_TYPE_NORMAL

            // Selects appropriate preview size and configures camera surface
            val previewSize = getPreviewOutputSize(
                Size(DESIGNED_CAMERA_SIZE.width, DESIGNED_CAMERA_SIZE.height)/*cameraView.display*/,
                characteristics,
                SurfaceHolder::class.java
            )
            // To ensure that size is set, initialize camera in the view's thread
            runCatching {
                LLog.i(ITAG, "Prepare to call initializeCamera. previewSize=$previewSize")
                initializeCamera(previewSize.width, previewSize.height)
            }.getOrElse {
                LLog.e(ITAG, "=====> Finally openCamera error <=====")
                ToastUtil.showErrorToast("Initialized camera error. Please try again later.")
            }
        }

        btnCameraRecord.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) doStartRecord() else stopRecord()
        }
    }

    override fun onStop() {
        stopRecord()
        super.onStop()
    }

    override fun onDestroy() {
        if (::camera2Helper.isInitialized) camera2Helper.stopCameraThread()
        super.onDestroy()
    }

    private fun doStartRecord() {
        lifecycleScope.launch(Dispatchers.IO) {
            // CAMERA_SIZE_NORMAL & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=5
            // BITRATE_MODE_CQ: 348.399kB/s
            // BITRATE_MODE_CBR: 85.875kB/s
            // BITRATE_MODE_VBR: 84.929kB/s
            // CAMERA_SIZE_HIGH & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=3
            // BITRATE_MODE_CBR: 113.630kB/s
            camera2Helper.Builder(DESIGNED_CAMERA_SIZE.width, DESIGNED_CAMERA_SIZE.height).run {
//        camera2ComponentBuilder.previewInFullscreen = true
                quality = Camera2ComponentHelper.BITRATE_LOW
                // On Nexus6 Camera Fps should be CAMERA_FPS_VERY_HIGH - Range(30, 30)
                cameraFps = Camera2ComponentHelper.CAMERA_FPS_LOW
                videoFps = Camera2ComponentHelper.VIDEO_FPS_FREQUENCY_NORMAL
                iFrameInterval = 5
                bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                build()
            }
            camera2Helper.outputH264ForDebug = true
            camera2Helper.setEncodeListener(object : Camera2ComponentHelper.EncodeDataUpdateListener {
                override fun onUpdate(h264Data: ByteArray) {
                    LLog.d(ITAG, "Get encoded video data length=${h264Data.size}")
                }
            })
            camera2Helper.initDebugOutput()

            camera2Helper.extraInitializeCameraForRecording()
            camera2Helper.setImageReaderForRecording()
            camera2Helper.startRecording()
        }
    }

    private fun stopRecord() {
        if (::camera2Helper.isInitialized) {
            camera2Helper.run {
                closeDebugOutput()
                if (isRecording) stopRecording() else closeCamera()
                initializeCamera(previewWidth, previewHeight)
            }
        }
    }
}