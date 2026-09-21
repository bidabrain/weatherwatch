package com.weatherwatch.watch.data

import android.content.Context
import android.util.Log
import com.weatherwatch.core.model.City
import kotlinx.serialization.json.Json

/**
 * 记住用户选的城市。
 *
 * 存完整 City（含坐标）而不是一个 id —— 城市来自在线搜索，
 * 本地没有可供反查的表，只存 id 的话重启后就找不回坐标了。
 */
class CityStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** 首次启动返回 null，界面据此直接打开搜索页 */
    fun load(): City? {
        val raw = prefs.getString(KEY_CITY, null)
        if (raw == null) {
            Log.i(TAG, "no saved city")
            return null
        }
        return try {
            json.decodeFromString(City.serializer(), raw).also {
                Log.i(TAG, "loaded city=" + it.name + " id=" + it.id)
            }
        } catch (e: Throwable) {
            // 曾经这里是 runCatching{}.getOrNull()，解析失败被静默吞掉，
            // 表现就是"每次打开都要重选城市"而没有任何线索。绝不再这样。
            Log.w(TAG, "saved city unreadable, raw=" + raw, e)
            null
        }
    }

    fun save(city: City) {
        val raw = json.encodeToString(City.serializer(), city)
        // 用 commit() 而不是 apply()：apply 是异步落盘，
        // ColorOS 杀后台很凶，进程被强杀时排队中的写会丢。
        // 这里只是一个几十字节的字符串，同步写的代价可以忽略。
        val ok = prefs.edit().putString(KEY_CITY, raw).commit()
        Log.i(TAG, "save city=" + city.name + " committed=" + ok)
    }

    private companion object {
        const val TAG = "CityStore"
        const val PREFS = "weatherwatch"
        const val KEY_CITY = "city"
    }
}
