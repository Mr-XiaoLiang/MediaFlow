package com.lollipop.mediaflow.upgrade

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

    }

    class Assets(
        val name: String,
        val url: String
    )

}