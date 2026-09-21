package com.weatherwatch.watch.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

/**
 * 设备状态查询。
 *
 * **绝对不要按 transport 过滤网络。** M0 实测：OPPO Watch 3 把手机的网络
 * 以 VPN 隧道 (tun0) 形式下发，transports = [BLUETOOTH, VPN]，
 * 任何 `addTransportType(TRANSPORT_WIFI)` 之类的约束都会判定"无网络"。
 * 只看 INTERNET + VALIDATED 这两个能力。
 */
class DeviceState(private val context: Context) {

    fun hasUsableNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** 电量百分比；取不到时返回 100，宁可多同步一次也不要莫名其妙不刷新。 */
    fun batteryPct(): Int {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return 100
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (pct in 0..100) pct else 100
    }
}
