package com.lollipop.mediaflow.data

import android.content.Context
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.doAsync
import com.lollipop.mediaflow.tools.onUI
import org.json.JSONObject
import java.io.File

class ConfigHelper(
    val name: String
) {

    private val log = registerLog()

    private var configFile: File? = null

    val jsonConfig = JSONObject()

    private fun getConfigFile(context: Context): File? {
        try {
            val configDir = File(context.filesDir, "config")
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            return File(configDir, name)
        } catch (e: Throwable) {
            log.e("getConfigFile", e)
        }
        return null
    }

    private fun optFile(context: Context?): File? {
        val file = configFile
        if (file != null) {
            return file
        }
        if (context == null) {
            return null
        }
        return getConfigFile(context)
    }

    fun load(context: Context, onEnd: () -> Unit) {
        doAsync {
            val newConfig = optFile(context)?.readText()?.let { JSONObject(it) }
            onUI {
                if (newConfig != null) {
                    val keys = newConfig.keys()
                    for (key in keys) {
                        jsonConfig.put(key, newConfig.opt(key))
                    }
                }
                onEnd()
            }
        }
    }

    fun save(context: Context? = null) {
        doAsync {
            val file = optFile(context)
            file?.writeText(jsonConfig.toString())
        }
    }

}