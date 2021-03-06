package com.leovp.androidbase.utils.network

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.leovp.androidbase.utils.log.LogContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketAddress
import java.util.*

/**
 * Author: Michael Leo
 * Date: 20-6-12 上午11:37
 *
 */
object NetworkUtil {
    private const val TAG = "NetworkUtil"

    const val NETWORK_PING_DELAY_HIGH = 130
    const val NETWORK_PING_DELAY_VERY_HIGH = 200

    const val NETWORK_SIGNAL_STRENGTH_BAD = 2
    const val NETWORK_SIGNAL_STRENGTH_VERY_BAD = 1

    fun isOnline(ctx: Context) = getNetworkInfo(ctx)?.isConnected ?: false
    fun isOffline(ctx: Context) = !isOnline(ctx)

    /**
     * **Need following permission:**
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @SuppressLint("MissingPermission")
    private fun getNetworkInfo(ctx: Context) =
        (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo

    /**
     * Returns the latency to a given server in milliseconds by issuing a ping command.
     * System will issue NUMBER_OF_PACKTETS ICMP Echo Request packet each having size of 56 bytes
     * every second, and returns the avg latency of them.
     *
     * @return Return the latency which getting from ping command. Return -2 which indicates network is offline.
     * Return -1 indicates get ping error.
     */
    fun getLatency(ctx: Context, ipAddress: String, numberOfPackages: Int): Double {
        if (isOffline(ctx)) {
            return (-2).toDouble()
        }
        var inputLine: String?
        try {
            val pingCommand = String.format(Locale.getDefault(), "/system/bin/ping -c %d %s", numberOfPackages, ipAddress)
            // Execute the command on the environment interface
            val process = Runtime.getRuntime().exec(pingCommand)
            // Gets the input stream to get the output of the executed command
            BufferedReader(InputStreamReader(process.inputStream)).use {
                inputLine = it.readLine()
                while (inputLine != null) {
                    if (inputLine!!.isNotEmpty() && inputLine!!.contains("avg")) {
                        // when we get to the last line of executed ping command
                        break
                    }
                    inputLine = it.readLine()
                }
            }
        } catch (e: IOException) {
            return (-1).toDouble()
        }

        // Extracting the average round trip time from the inputLine string
        return try {
            val afterEqual = inputLine!!.substring(inputLine!!.indexOf("=")).trim { it <= ' ' }
            val afterFirstSlash = afterEqual.substring(afterEqual.indexOf('/') + 1).trim { it <= ' ' }
            val strAvgRtt = afterFirstSlash.substring(0, afterFirstSlash.indexOf('/'))
            strAvgRtt.toDouble()
        } catch (e: Exception) {
            (-1).toDouble()
        }
    }

    fun getNetworkType(ctx: Context) = if (getNetworkInfo(ctx) != null) {
        getNetworkInfo(ctx)?.type ?: -1
    } else {
        -1
    }

    /**
     * **Need following permission:**
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
     * ```
     */
    @SuppressLint("MissingPermission")
    fun getWifiNetworkStats(ctx: Context): IntArray? {
        val linkSpeed: Int
        val wifiScore: Int
        val wifiScoreIn5: Int
        val rssi: Int
        if (getNetworkType(ctx) == ConnectivityManager.TYPE_WIFI) {
            val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wi = wifiManager.connectionInfo
            linkSpeed = wi.linkSpeed
            /* Rssi
             * 0 —— (-55)dBm        满格(4格)信号
             * (-55) —— (-70)dBm    3格信号
             * (-70) —— (-85)dBm    2格信号
             * (-85) —— (-100)dBm   1格信号
             */rssi = wi.rssi
            wifiScore = WifiManager.calculateSignalLevel(rssi, 100)
            wifiScoreIn5 = WifiManager.calculateSignalLevel(rssi, 5)
        } else {
            return null
        }
        return intArrayOf(linkSpeed, rssi, wifiScoreIn5, wifiScore)
    }

    fun isHostReachable(hostname: String?, port: Int, timeoutInMillis: Int): Boolean {
        var connected = false
        runCatching {
            val socket = Socket()
            val socketAddress: SocketAddress = InetSocketAddress(hostname, port)
            socket.connect(socketAddress, timeoutInMillis)
            if (socket.isConnected) {
                connected = true
                socket.close()
            }
        }.onFailure {
            LogContext.log.e(TAG, "isHostReachable error", it)
        }
        return connected
    }

    /**
     * Need following permission
     * ```xml
     * <uses-permission name="android.permission.ACCESS_WIFI_STATE" />
     * ```
     */
    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getMacAddressBeforeAndroidM(application: Application): String {
        val wifiMan = application.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInf = wifiMan.connectionInfo
        // DEFAULT_MAC_ADDRESS = "02:00:00:00:00:00"
        // Please check WifiInfo#DEFAULT_MAC_ADDRESS
        return if ("02:00:00:00:00:00".equals(wifiInf.macAddress, ignoreCase = true)) "" else wifiInf.macAddress
    }

    fun getMacAddress(application: Application): String {
        return runCatching {
            var address = getMacAddressBeforeAndroidM(application)
            if (address.isNotBlank()) {
                return address
            }
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val netWork = interfaces.nextElement()
                val by = netWork.hardwareAddress
                if (by == null || by.isEmpty()) {
                    continue
                }
                val builder = StringBuilder()
                for (b in by) {
                    builder.append(String.format("%02X:", b))
                }
                if (builder.isNotEmpty()) {
                    builder.deleteCharAt(builder.length - 1)
                }
                val mac = builder.toString()
                if (netWork.name == "wlan0") {
                    address = mac
                }
            }
            address
        }.getOrDefault("")
    }
}