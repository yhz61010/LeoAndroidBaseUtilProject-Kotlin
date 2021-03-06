package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.utils.device.CaptureUtil
import com.leovp.androidbase.utils.media.ImageUtil
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import java.io.File

class TakeScreenshotActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_screenshot)
    }

    fun onScreenshot(view: View) {
        val bitmap = CaptureUtil.takeScreenshot(window)
        bitmap?.let {
            val screenshotFile = File(getExternalFilesDir(null), "screenshot.jpg")
            ImageUtil.writeBitmapToFile(screenshotFile, it, 100)
            ToastUtil.showToast("Screenshot is saved in ${screenshotFile.absolutePath}")
        }
    }
}