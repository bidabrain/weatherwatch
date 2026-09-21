package com.weatherwatch.watch.data

import android.util.Log
import com.weatherwatch.core.model.WeatherBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 快照持久化。一城一文件，整体替换。
 *
 * 没有用 Room：查询需求只有"按 cityId 取最新一条"，
 * 为此拖进 KSP 注解处理器不划算。一份快照 6~8KB，文件足够。
 *
 * 写入必须原子 —— 手表随时可能被系统杀掉，
 * 半个文件会让下次启动直接崩在解析上。
 */
class SnapshotStore(private val dir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun read(cityId: String): WeatherBundle? = withContext(Dispatchers.IO) {
        val f = fileFor(cityId)
        if (!f.exists()) return@withContext null
        try {
            json.decodeFromString(WeatherBundle.serializer(), f.readText())
        } catch (e: Throwable) {
            // 解析失败说明文件损坏或模型结构变了。
            // 删掉当无缓存处理，绝不让用户卡在崩溃循环里。
            Log.w(TAG, "snapshot corrupt, dropping: " + e.message)
            runCatching { f.delete() }
            null
        }
    }

    suspend fun write(bundle: WeatherBundle) = withContext(Dispatchers.IO) {
        if (!dir.exists()) dir.mkdirs()
        val target = fileFor(bundle.cityId)
        val tmp = File(dir, bundle.cityId + ".json.tmp")
        try {
            tmp.writeText(json.encodeToString(WeatherBundle.serializer(), bundle))
            // rename 在同一文件系统内是原子的：要么旧文件完整，要么新文件完整
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) error("rename failed")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "snapshot write failed: " + e.message)
            runCatching { tmp.delete() }
        }
    }

    private fun fileFor(cityId: String) = File(dir, cityId + ".json")

    private companion object {
        const val TAG = "SnapshotStore"
    }
}
