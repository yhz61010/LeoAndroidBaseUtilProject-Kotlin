package com.leovp.screenshot.base

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午1:51
 */
interface ScreenDataListener {
    fun onDataUpdate(buffer: Any, flags: Int)
}