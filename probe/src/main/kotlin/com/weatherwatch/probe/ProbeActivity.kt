package com.weatherwatch.probe

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.concurrent.thread

/**
 * M0 探针：回答两个问题
 *   1. 只连蓝牙（关掉手表 Wi-Fi）时，第三方 App 能不能通过手机上网？
 *   2. 这块屏的真实 density / dp 尺寸是多少？（决定 UI 全部尺寸）
 */
class ProbeActivity : Activity() {

    private lateinit var out: TextView
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        out = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setPadding(12, 12, 12, 12)
            setTextIsSelectable(true)
        }
        val button = Button(this).apply {
            text = "RUN"
            setOnClickListener { runProbe() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(button)
            addView(ScrollView(this@ProbeActivity).apply { addView(out) })
        }
        setContentView(root)

        runProbe()
    }

    private fun log(line: String) = ui.post { out.append(line + "\n") }

    private fun runProbe() {
        ui.post { out.text = "" }
        reportDevice()
        reportScreen()
        reportNetwork()
        thread { reportHttp() }
    }

    private fun reportDevice() {
        log("== DEVICE ==")
        log("${Build.MANUFACTURER} ${Build.MODEL}")
        log("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        log("fingerprint=${Build.FINGERPRINT}")
    }

    private fun reportScreen() {
        val dm = resources.displayMetrics
        log("")
        log("== SCREEN ==")
        log("px      = ${dm.widthPixels} x ${dm.heightPixels}")
        log("density = ${dm.density}  (densityDpi=${dm.densityDpi})")
        log("dp      = ${(dm.widthPixels / dm.density).toInt()} x ${(dm.heightPixels / dm.density).toInt()}")
        log("xdpi/ydpi = ${dm.xdpi} / ${dm.ydpi}")
        log("fontScale = ${resources.configuration.fontScale}")
    }

    private fun reportNetwork() {
        log("")
        log("== NETWORK ==")
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val active = cm.activeNetwork
        log("activeNetwork = $active")
        if (active == null) {
            log("  !! 系统认为没有任何可用网络")
        }

        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n)
            val link = cm.getLinkProperties(n)
            val transports = buildList {
                if (caps == null) return@buildList
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("BLUETOOTH")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("CELLULAR")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            }
            val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            log("net $n ${if (n == active) "<ACTIVE>" else ""}")
            log("  transports = $transports")
            log("  INTERNET=$internet VALIDATED=$validated")
            log("  iface = ${link?.interfaceName}  dns = ${link?.dnsServers}")
        }
    }

    /** 真的发请求。caps 说有网不等于发得出去，必须实测。 */
    private fun reportHttp() {
        log("")
        log("== HTTP (real traffic) ==")

        dns("www.gstatic.com")
        dns("api.open-meteo.com")

        http("https://www.gstatic.com/generate_204")
        http("https://api.open-meteo.com/v1/forecast?latitude=30.27&longitude=120.15&current=temperature_2m")
        http("https://api.weather.com/v3/location/search?query=hangzhou&language=zh-CN&format=json&apiKey=PLACEHOLDER")

        log("")
        log("== DONE ==")
    }

    private fun dns(host: String) {
        val t0 = System.currentTimeMillis()
        try {
            val addrs = InetAddress.getAllByName(host).joinToString { it.hostAddress ?: "?" }
            log("DNS  $host -> $addrs  (${System.currentTimeMillis() - t0}ms)")
        } catch (e: Throwable) {
            log("DNS  $host -> FAIL ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun http(url: String) {
        val t0 = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val body = try {
                (if (code < 400) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText()?.take(120) ?: ""
            } catch (e: Throwable) {
                "<body read failed: ${e.message}>"
            }
            log("GET  ${url.take(60)}")
            log("  -> $code  (${System.currentTimeMillis() - t0}ms)")
            if (body.isNotEmpty()) log("  body: $body")
        } catch (e: Throwable) {
            log("GET  ${url.take(60)}")
            log("  -> FAIL ${e.javaClass.simpleName}: ${e.message}  (${System.currentTimeMillis() - t0}ms)")
        } finally {
            conn?.disconnect()
        }
    }
}
