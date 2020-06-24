package com.ho1ho.camera2live.view

import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.media.CodecUtil
import com.ho1ho.camera2live.Camera2Component
import com.ho1ho.camera2live.Camera2Component.EncodeDataUpdateListener
import com.ho1ho.camera2live.Camera2Component.LensSwitchListener
import com.ho1ho.camera2live.R
import com.ho1ho.camera2live.base.DataProcessFactory

/**
 * Author: Michael Leo
 * Date: 20-6-23 下午3:34
 */
class CameraViewFragment : Fragment() {
    private lateinit var switchBtn: ToggleButton
    private lateinit var switchFlashBtn: ToggleButton
    private lateinit var camera2Component: Camera2Component
    private var previousLensFacing = CameraMetadata.LENS_FACING_BACK
    private lateinit var surfaceView: CameraSurfaceView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_camera_view, container, false)
        v.findViewById<ImageView>(R.id.ivBack).setOnClickListener { activity?.supportFragmentManager?.popBackStackImmediate() }
        v.findViewById<ImageView>(R.id.ivShot).setOnClickListener {}
        switchBtn = v.findViewById(R.id.switchFacing)
        switchBtn.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> camera2Component.switchCamera() }
        switchFlashBtn = v.findViewById(R.id.switchFlashBtn)
        switchFlashBtn.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> camera2Component.switchFlash() }
        val desiredSize = CAMERA_SIZE_HIGH

        // CAMERA_SIZE_NORMAL & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=5
        // BITRATE_MODE_CQ: 348.399kB/s
        // BITRATE_MODE_CBR: 85.875kB/s
        // BITRATE_MODE_VBR: 84.929kB/s

        // CAMERA_SIZE_HIGH & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=3
        // BITRATE_MODE_CBR: 113.630kB/s
        val camera2ComponentBuilder = Camera2Component(this).Builder(desiredSize[0], desiredSize[1])
        camera2ComponentBuilder.cameraSurfaceView = v.findViewById(R.id.cameraSurfaceView)
//        camera2ComponentBuilder.previewInFullscreen = true
        camera2ComponentBuilder.quality = Camera2Component.BITRATE_NORMAL
        // On Nexus6 Camera Fps should be CAMERA_FPS_VERY_HIGH - Range(30, 30)
        camera2ComponentBuilder.cameraFps = Camera2Component.CAMERA_FPS_VERY_HIGH
        camera2ComponentBuilder.videoFps = Camera2Component.VIDEO_FPS_FREQUENCY_HIGH
        camera2ComponentBuilder.iFrameInterval = 1
        camera2ComponentBuilder.bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        camera2Component = camera2ComponentBuilder.build()
        camera2Component.setDebugOutputH264(true)
        camera2Component.initializeCamera(previousLensFacing)
        camera2Component.setEncodeListener(object : EncodeDataUpdateListener {
            override fun onUpdate(h264Data: ByteArray) {
                Log.d(TAG, "Get encoded video data length=" + h264Data.size)
            }
        })
        camera2Component.setLensSwitchListener(object : LensSwitchListener {
            override fun onSwitch(lensFacing: Int) {
                Log.w(TAG, "lensFacing=$lensFacing")
                previousLensFacing = lensFacing
            }
        })
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        surfaceView = view.findViewById(R.id.cameraSurfaceView)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // To ensure that size is set, initialize camera in the view's thread
                view.post {
                    camera2Component.initDebugOutput()
                    camera2Component.encoderType = if (
                        CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
                        || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.Exynos.AVC.Encoder")
                        || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.MTK.VIDEO.ENCODER.AVC")
                    ) DataProcessFactory.ENCODER_TYPE_YUV_ORIGINAL
                    else DataProcessFactory.ENCODER_TYPE_NORMAL
                    camera2Component.openCameraAndGetData(previousLensFacing) // LENS_FACING_FRONT LENS_FACING_BACK
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }

    override fun onResume() {
        CLog.i(TAG, "CameraViewFragment onResume")
        super.onResume()
    }

    override fun onPause() {
        CLog.i(TAG, "CameraViewFragment onPause")
        camera2Component.closeDebugOutput()
        camera2Component.closeCameraAndStopRecord()
        super.onPause()
    }

    companion object {
        private const val TAG = "MainActivity"
        private val CAMERA_SIZE_EXTRA = intArrayOf(1080, 1920)
        private val CAMERA_SIZE_HIGH = intArrayOf(720, 1280)
        private val CAMERA_SIZE_NORMAL = intArrayOf(720, 960)
        private val CAMERA_SIZE_LOW = intArrayOf(480, 640)
    }
}