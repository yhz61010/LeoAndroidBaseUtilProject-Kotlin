package com.ho1ho.androidbase.utils

import android.content.Context
import com.ho1ho.androidbase.utils.file.FileUtil
import com.tencent.mars.xlog.Log
import com.tencent.mars.xlog.Xlog
import java.io.File

/**
 * Author: Michael Leo
 * Date: 20-4-20 上午11:39
 */
object CLog {
    private const val DEBUG_MODE = true
    private const val BASE_TAG = "LEO-Demo-"

    init {
        System.loadLibrary("c++_shared")
        System.loadLibrary("marsxlog")
    }

    fun init(context: Context) {
        val logDir = FileUtil.getLogDir(context, "xlog")
        Xlog.appenderOpen(
            if (DEBUG_MODE) Xlog.LEVEL_DEBUG else Xlog.LEVEL_INFO,
            Xlog.AppednerModeAsync,
            File(context.filesDir, "x-cache-dir").absolutePath,
            logDir.absolutePath,
            "main", 0, if (DEBUG_MODE) "" else context.packageName
        )
        Xlog.setConsoleLogOpen(DEBUG_MODE)
        Log.setLogImp(Xlog())
    }

    private fun getTagName(tag: String): String {
        return BASE_TAG + tag
    }

    @Suppress("unused")
    fun v(tag: String, message: String?) {
        Log.v(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    fun d(tag: String, message: String?) {
        Log.d(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    fun i(tag: String, message: String?) {
        Log.i(getTagName(tag), message ?: "[null]")
    }

    fun w(tag: String, message: String?) {
        Log.w(getTagName(tag), message ?: "[null]")
    }

    fun e(tag: String, message: String?) {
        Log.e(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    fun e(tag: String, throwable: Throwable?) {
        e(tag, null, throwable)
    }

    @Suppress("unused")
    fun f(tag: String, message: String?) {
        Log.f(getTagName(tag), message ?: "[null]")
    }

    @Suppress("unused")
    fun v(tag: String, message: String?, throwable: Throwable?) {
        Log.v(
            getTagName(tag),
            getMessage(message, throwable)
        )
    }

    @Suppress("unused")
    fun d(tag: String, message: String?, throwable: Throwable?) {
        Log.d(
            getTagName(tag),
            getMessage(message, throwable)
        )
    }

    @Suppress("unused")
    fun i(tag: String, message: String?, throwable: Throwable?) {
        Log.i(
            getTagName(tag),
            getMessage(message, throwable)
        )
    }

    @Suppress("unused")
    fun w(tag: String, message: String?, throwable: Throwable?) {
        Log.w(
            getTagName(tag),
            getMessage(message, throwable)
        )
    }

    @Suppress("unused")
    fun e(tag: String, message: String?, throwable: Throwable?) {
        Log.e(
            getTagName(tag),
            getMessage(message, throwable)
        )
    }

    @Suppress("unused")
    fun f(tag: String, message: String?, throwable: Throwable?) {
        Log.f(
            getTagName(tag),
            getMessage(message, throwable)
        )
    }

    private fun getMessage(message: String?, throwable: Throwable?): String {
        if (message == null && throwable == null) return "[Empty Message]"

        val sb = StringBuilder()
        if (!message.isNullOrBlank()) {
            sb.append(message)
        }
        if (throwable == null) {
            return sb.toString()
        }
        return if (message == null) {
            sb.append(getStackTraceString(throwable)).toString()
        } else {
            sb.append(" : ").append(getStackTraceString(throwable)).toString()
        }
    }

    @Suppress("WeakerAccess")
    fun getStackTraceString(tr: Throwable?): String {
        return android.util.Log.getStackTraceString(tr)
    }

    // Usage: getStackTraceString(Thread.currentThread().getStackTrace())
    @Suppress("unused")
    fun getStackTraceString(elements: Array<StackTraceElement>?): String {
        if (elements.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        elements.forEach { sb.append('\n').append(it.toString()) }
        return sb.toString()
    }

    @Suppress("unused")
    fun flushLog(isSync: Boolean = true) {
        Log.appenderFlush(isSync)
    }

    @Suppress("unused")
    fun closeLog() {
        Log.appenderClose()
    }
}