package com.ho1ho.leoandroidbaseutil.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.leoandroidbaseutil.ui.sharescreen.master.ScreenShareSetting
import com.ho1ho.screenshot.ScreenCapture
import com.ho1ho.screenshot.base.ScreenDataListener
import com.ho1ho.screenshot.base.ScreenshotStrategy
import kotlinx.android.synthetic.main.activity_screenshot_record_h264.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*


class ScreenshotRecordH264Activity : BaseDemonstrationActivity() {

    private val CODE_IMAGE_SEARCH = 1111
    private var selectedImgUris = ArrayList<Uri>()

    private val screenDataListener = object : ScreenDataListener {
        override fun onDataUpdate(buffer: Any) {
            val buf = buffer as ByteArray
            LLog.i(ITAG, "Get h264 data[${buf.size}]")
//            if (outputH264File) {
//                try {
//                    videoH264Os.write(buf)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//            screenDataUpdateListener?.onUpdate(buffer)
            // Bitmap for screenshot
//            val bitmap = buffer as Bitmap
//            FileUtil.writeBitmapToFile(bitmap, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshot_record_h264)

        val timer = Timer("capture-screen-record-th")
        toggleBtn.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val width = window.decorView.rootView.width
                val height = window.decorView.rootView.height
                LLog.d(ITAG, "$width x $height=${width * height}")

                val screenInfo = DeviceUtil.getResolution(this)
                val setting = ScreenShareSetting(
                    (screenInfo.x * 0.8F).toInt() / 16 * 16,
                    (screenInfo.y * 0.8F).toInt() / 16 * 16,
                    DeviceUtil.getDensity(this)
                )
                setting.fps = 20f

                val cs = CoroutineScope(Dispatchers.IO).launch {
                    val screenProcessor = ScreenCapture.Builder(
                        setting.width, // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
                        setting.height, // 800 1024 1280
                        setting.dpi,
                        null,
                        ScreenCapture.SCREEN_CAPTURE_TYPE_IMAGE,
                        screenDataListener
                    ).setFps(setting.fps)
                        .setSampleSize(1)
                        .build().apply {
                            onInit()
                            onStart()
                        }
                    (screenProcessor as ScreenshotStrategy).startRecord(window)
                }
            } else {
                timer.cancel()
//                comp.onRelease()
            }
        }
    }

    fun conver_argb_to_i420(i420: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0 // Y start index
        var uIndex = frameSize // U start index
        var vIndex = frameSize + width * height / 4 // V start index: w*h*5/4
        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                a = (argb[index] and 0xff000000.toInt()) shr 24 //  is not used obviously
                R = (argb[index] and 0xff0000) shr 16
                G = (argb[index] and 0xff00) shr 8
                B = (argb[index] and 0xff) shr 0

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) shr 8) + 16
                U = ((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128
                V = ((112 * R - 94 * G - 18 * B + 128) shr 8) + 128

                // I420(YUV420p) -> YYYYYYYY UU VV
                i420[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    i420[vIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                    i420[uIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                }
                index++
            }
        }
    }

    // ==========================

    fun performImagesSearch(activity: AppCompatActivity, code: Int) {
        performFileSearch(
            activity, code, true,
            "image/*",
            "image/png", "image/jpeg", "image/tiff"
        )
    }

    fun performFileSearch(
        activity: AppCompatActivity, code: Int, multiple: Boolean, type: String,
        vararg mimetype: String
    ) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type
            putExtra(Intent.EXTRA_MIME_TYPES, mimetype)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        }

        activity.startActivityForResult(intent, code)
    }

    fun onSelectPicClick(view: View) {
        performImagesSearch(this, CODE_IMAGE_SEARCH)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        addImages(data!!)
    }

    private fun addImages(data: Intent) {
        if (data != null) {
            if (data.data != null) {
                selectedImgUris.add(data.data!!)
            } else if (data.clipData != null) {
                val cd = data.clipData!!
                val unsorted = ArrayList<Uri>()
                for (i in 0 until cd.itemCount) {
                    val item = cd.getItemAt(i)
                    unsorted.add(item.uri)
                }

                // System file picker doesn't guarantee any kind of order when
                // Multiple files are selected, so I at least sort by path/filename
                selectedImgUris.addAll(unsorted.sortedBy { it.encodedPath })
            }
        }

    }
}