package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import android.view.View
import com.ho1ho.androidbase.utils.system.KeepAlive
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity

class KeepAliveActivity : BaseDemonstrationActivity() {

    private val keepAlive: KeepAlive by lazy { KeepAlive(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keep_alive)
    }

    fun onKeepAliveClick(view: View) {
        keepAlive.keepAlive()
    }

    fun onStopClick(view: View) {
        keepAlive.release()
    }
}