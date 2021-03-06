package com.leovp.androidbase.ui

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import androidx.annotation.LayoutRes
import com.leovp.androidbase.exts.android.canDrawOverlays
import kotlin.math.abs


/**
 * Author: Michael Leo
 * Date: 20-3-3 下午3:40
 *
 * Need permission: `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`
 *
 * if `enableFullScreen` is `true`, the `enableDrag` parameter will be ignored.
 *
 * @see [Float View](https://stackoverflow.com/a/53092436)
 * @see [Float View Github](https://github.com/aminography/FloatingWindowApp)
 */
class FloatViewManager constructor(
    private val context: Context,
    @LayoutRes private val layoutId: Int,
    @Suppress("WeakerAccess") val enableDrag: Boolean = true,
    @Suppress("WeakerAccess") val enableFullScreen: Boolean = false
) {

    private var windowManager: WindowManager? = null
        get() {
            if (field == null) field = (context.getSystemService(WINDOW_SERVICE) as WindowManager)
            return field
        }

    @Suppress("WeakerAccess")
    var floatView: View = LayoutInflater.from(context).inflate(layoutId, null)
        private set

    private lateinit var layoutParams: WindowManager.LayoutParams

    private var lastX: Int = 0
    private var lastY: Int = 0
    private var firstX: Int = 0
    private var firstY: Int = 0

    private var isShowing = false
    private var touchConsumedByMove = false


    private val onTouchListener = View.OnTouchListener { view, event ->
        if (!enableDrag or enableFullScreen) return@OnTouchListener false
        val totalDeltaX = lastX - firstX
        val totalDeltaY = lastY - firstY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                firstX = lastX
                firstY = lastY
            }
            MotionEvent.ACTION_UP -> {
                view.performClick()
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX.toInt() - lastX
                val deltaY = event.rawY.toInt() - lastY
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                if (abs(totalDeltaX) >= 5 || abs(totalDeltaY) >= 5) {
                    if (event.pointerCount == 1) {
                        layoutParams.x += deltaX
                        layoutParams.y += deltaY
                        touchConsumedByMove = true
                        windowManager?.apply {
                            updateViewLayout(floatView, layoutParams)
                        }
                    } else {
                        touchConsumedByMove = false
                    }
                } else {
                    touchConsumedByMove = false
                }
            }
            else -> {
            }
        }
        touchConsumedByMove
    }

    init {
        floatView.setOnTouchListener(onTouchListener)

        layoutParams = WindowManager.LayoutParams().apply {
            format = PixelFormat.TRANSLUCENT
            flags = if (enableDrag) {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            } else {
                // FLAG_NOT_TOUCHABLE will bubble the event to the bottom layer.
                // However the float layer itself can not be touched anymore.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            }
            @Suppress("DEPRECATION")
            type = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else -> WindowManager.LayoutParams.TYPE_TOAST
            }

            gravity = Gravity.CENTER
            width = if (enableFullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
            height = if (enableFullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
        }
    }

    fun show() {
        if (context.canDrawOverlays) {
            dismiss()
            isShowing = true
            windowManager?.addView(floatView, layoutParams)
        }
    }

    @Suppress("unused")
    fun dismiss() {
        if (isShowing) {
            windowManager?.removeView(floatView)
            isShowing = false
        }
    }
}