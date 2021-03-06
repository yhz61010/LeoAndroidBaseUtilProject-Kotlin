package com.leovp.androidbase.utils.notch.impl

import android.annotation.TargetApi
import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.view.Window
import com.leovp.androidbase.exts.android.calculateNotchRect
import com.leovp.androidbase.exts.android.getDimenInPixel
import com.leovp.androidbase.utils.device.DeviceProp
import com.leovp.androidbase.utils.notch.INotchScreen
import com.leovp.androidbase.utils.notch.INotchScreen.NotchSizeCallback
import java.util.*

@TargetApi(Build.VERSION_CODES.O)
class MiNotchScreen : INotchScreen {
    override fun hasNotch(activity: Activity) = isNotch

    override fun setDisplayInNotch(activity: Activity) {
        val flag = 0x00000100 or 0x00000200 or 0x00000400
        runCatching {
            val method = Window::class.java.getMethod("addExtraFlags", Int::class.javaPrimitiveType)
            method.invoke(activity.window, flag)
        }.onFailure { it.printStackTrace() }
    }

    override fun getNotchRect(activity: Activity, callback: NotchSizeCallback) {
        val rect = calculateNotchRect(activity, getNotchWidth(), getNotchHeight())
        val rects = ArrayList<Rect>()
        rects.add(rect)
        callback.onResult(rects)
    }

    companion object {
        private val isNotch: Boolean = runCatching { DeviceProp.getSystemProperty("ro.miui.notch").toInt() == 1 }.getOrDefault(false)
        fun getNotchHeight() = getDimenInPixel("notch_height")
        fun getNotchWidth() = getDimenInPixel("notch_width")
    }
}