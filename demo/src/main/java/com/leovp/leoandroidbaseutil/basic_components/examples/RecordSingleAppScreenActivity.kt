package com.leovp.leoandroidbaseutil.basic_components.examples

import android.media.MediaCodec
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.leovp.androidbase.exts.android.densityDpi
import com.leovp.androidbase.exts.android.getAvailableResolution
import com.leovp.androidbase.exts.kotlin.ITAG
import com.leovp.androidbase.exts.kotlin.toHexString
import com.leovp.androidbase.utils.file.FileUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareSetting
import com.leovp.screenshot.ScreenCapture
import com.leovp.screenshot.base.ScreenDataListener
import com.leovp.screenshot.base.ScreenshotStrategy
import kotlinx.android.synthetic.main.activity_screenshot_record_h264.*
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream


class RecordSingleAppScreenActivity : BaseDemonstrationActivity() {

    private lateinit var videoH264OsForDebug: BufferedOutputStream

    private val screenDataListener = object : ScreenDataListener {
        override fun onDataUpdate(buffer: Any, flags: Int) {
            val buf = buffer as ByteArray
            when (flags) {
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> LogContext.log.i(ITAG, "Get h264 data[${buf.size}]=${buf.toHexString()}")
                MediaCodec.BUFFER_FLAG_KEY_FRAME -> LogContext.log.i(ITAG, "Get h264 data Key-Frame[${buf.size}]")
                else -> LogContext.log.i(ITAG, "Get h264 data[${buf.size}]")
            }
            videoH264OsForDebug.write(buf)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshot_record_h264)

        val file = FileUtil.getBaseDirString(this, "output")
        videoH264OsForDebug = BufferedOutputStream(FileOutputStream(File(file, "screen.h264")))

        val screenInfo = getAvailableResolution()
        val setting = ScreenShareSetting(
            (screenInfo.x * 0.8F / 16).toInt() * 16,
            (screenInfo.y * 0.8F / 16).toInt() * 16,
            densityDpi
        )
        // FIXME: Seems does not work. Check bellow setKeyFrameRate
        setting.fps = 5f

        val screenProcessor = ScreenCapture.Builder(
            setting.width, // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
            setting.height, // 800 1024 1280
            setting.dpi,
            null,
            ScreenCapture.BY_IMAGE,
            screenDataListener
        ).setFps(setting.fps)
            .setKeyFrameRate(20)
            .setQuality(80)
            .setSampleSize(1)
            .build()

        toggleBtn.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                (screenProcessor as ScreenshotStrategy).startRecord(this)
            } else {
                videoH264OsForDebug.flush()
                videoH264OsForDebug.close()
                screenProcessor.onRelease()
            }
        }
    }

    fun onShowToastClick(view: View) {
        ToastUtil.showToast("Custom Toast")
    }

    fun onShowDialogClick(view: View) {
        AlertDialog.Builder(this)
            .setTitle("Title")
            .setMessage("This is a dialog")
            .setPositiveButton("OK") { dlg, _ ->
                dlg.dismiss()
            }
            .setNeutralButton("Cancel", null)
            .create()
            .show()
    }
}