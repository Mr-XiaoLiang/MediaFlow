package com.lollipop.common.upgrade

import com.lollipop.common.tools.safeRun
import org.json.JSONArray
import org.json.JSONObject

class GithubReleaseInfo(
    val tagName: String,
    val assets: List<Assets>,
    val updateInfo: String
) {

    companion object {

        fun parseLatest(json: JSONObject): GithubReleaseInfo {
            val tagName = json.optString("tag_name") ?: ""
            val assets = findFirstDownloadUrl(json.optJSONArray("assets"))
            val updateInfo = json.optString("body") ?: ""
            return GithubReleaseInfo(
                tagName = tagName,
                assets = assets,
                updateInfo = updateInfo
            )
        }

        private fun findFirstDownloadUrl(assetsArray: JSONArray?): List<Assets> {
            assetsArray ?: return emptyList()
            val resultList = mutableListOf<Assets>()
            val assetsLength = assetsArray.length()
            for (i in 0 until assetsLength) {
                val asset = assetsArray.optJSONObject(i) ?: continue
                val downloadUrl = asset.optString("browser_download_url") ?: ""
                val name = asset.optString("name")
                if (downloadUrl.isNotEmpty() && downloadUrl.endsWith("apk", ignoreCase = true)) {
                    resultList.add(Assets(name = name, url = downloadUrl))
                }
            }
            return resultList
        }

        private fun parseVersionName(value: String): String {
            if (value.isEmpty()) {
                return ""
            }
            return safeRun {
                if (value.startsWith("V")) {
                    value.substring(1)
                } else {
                    value
                }
            }.getOrNull() ?: value
        }

        private fun parseVersionCode(value: String): Int {
            return safeRun {
                val name = parseVersionName(value)
                if (value.isEmpty()) {
                    return@safeRun 0
                }
                val levelArray = name.split(".")
                var version = 0
                for (level in levelArray) {
                    version *= 100
                    try {
                        version += level.toInt()
                    } catch (_: Throwable) {
                    }
                }
                version
            }.getOrNull() ?: 0
        }

    }

    class Assets(
        val name: String,
        val url: String
    )

    val versionName: String by lazy {
        parseVersionName(tagName)
    }

    val versionCode: Int by lazy {
        parseVersionCode(tagName)
    }

}