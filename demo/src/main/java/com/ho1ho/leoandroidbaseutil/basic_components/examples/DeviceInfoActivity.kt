package com.ho1ho.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.androidbase.utils.media.CodecUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity
import kotlinx.android.synthetic.main.activity_device_info.*

class DeviceInfoActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)

        val deviceInfo = DeviceUtil.getDeviceInfo(this)
        tv.text = deviceInfo
        LLog.i(TAG, deviceInfo)

//        CodecUtil.getEncoderListByMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC).forEach { LLog.i(TAG, "Name: ${it.name}") }
        CodecUtil.getAllSupportedCodecList().forEach { LLog.i(TAG, "Name: ${it.name}") }
    }

    companion object {
        private const val TAG = "DeviceInfo"
    }
}