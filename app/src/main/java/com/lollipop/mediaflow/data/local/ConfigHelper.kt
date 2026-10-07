package com.lollipop.mediaflow.data.local

import android.content.Context
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.tools.TaskResult
import com.lollipop.common.tools.mapValue
import com.lollipop.common.tools.onFailure
import com.lollipop.common.tools.onSuccess
import com.lollipop.common.tools.safeRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class ConfigHelper(val name: String) {

    private val log = registerLog()

    private var configFile: File? = null

    val jsonConfig = JSONObject()

    private fun getConfigFile(context: Context): TaskResult<File> {
        return safeRun {
            val configDir = File(context.filesDir, "config")
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            File(configDir, name)
        }
    }

    private fun optFile(context: Context?): TaskResult<File> {
        val file = configFile
        if (file != null) {
            return TaskResult.Success(file)
        }
        if (context == null) {
            return TaskResult.Failure(IllegalArgumentException("context is null"))
        }
        return getConfigFile(context)
    }

    suspend fun load(context: Context): TaskResult<JSONObject> {
        return withContext(Dispatchers.IO) {
            optFile(context).mapValue {
                it.readText()
            }.mapValue {
                JSONObject(it)
            }.onSuccess { newConfig ->
                withContext(Dispatchers.Main) {
                    val keys = newConfig.keys()
                    for (key in keys) {
                        jsonConfig.put(key, newConfig.opt(key))
                    }
                }
            }.onFailure {
                log.e("load config failed", it)
            }
        }
    }

    suspend fun save(context: Context? = null): TaskResult<Unit> {
        return withContext(Dispatchers.IO) {
            optFile(context).mapValue {
                it.writeText(jsonConfig.toString())
            }.onFailure {
                log.e("save config failed", it)
            }
        }
    }

}