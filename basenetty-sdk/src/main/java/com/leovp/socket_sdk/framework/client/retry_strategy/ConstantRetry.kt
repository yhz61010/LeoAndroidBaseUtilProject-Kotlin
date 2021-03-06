package com.leovp.socket_sdk.framework.client.retry_strategy

import com.leovp.socket_sdk.framework.client.retry_strategy.base.RetryStrategy

/**
 * Author: Michael Leo
 * Date: 20-7-22 下午6:14
 */
class ConstantRetry(private val maxTimes: Int = 10, private val delayInMillSec: Long = 2000L) : RetryStrategy {
    override fun getMaxTimes(): Int {
        return maxTimes
    }

    override fun getDelayInMillSec(currentRetryTimes: Int): Long {
        return delayInMillSec
    }
}