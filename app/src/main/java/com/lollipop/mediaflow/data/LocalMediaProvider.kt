package com.lollipop.mediaflow.data

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.lollipop.mediaflow.data.MediaDatabase.CacheInfo
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.doAsync
import com.lollipop.mediaflow.tools.onUI
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

object LocalMediaProvider {

    private val dirCache = ConcurrentHashMap<String, MediaInfo.Directory>()
    private val topCache = CopyOnWriteArrayList<MediaInfo>()

    var currentModeId = 0L
        private set

    val cacheDir: Map<String, MediaInfo.Directory>
        get() {
            return dirCache
        }

    val topMedia: List<MediaInfo>
        get() {
            return topCache
        }

    private val log by lazy {
        registerLog()
    }

    private fun resetCache(map: Map<String, MediaInfo.Directory>, top: List<MediaInfo>) {
        log.i("resetCache.map = ${map.size}, top = ${top.size}")
        dirCache.clear()
        dirCache.putAll(map)
        topCache.clear()
        topCache.addAll(top)
    }

    fun fetchAllCache(db: MediaDatabase, onEnd: () -> Unit) {
        log.i("fetchAllCache.onStart")
        doAsync {
            val fetchResult = fetchAllCacheSync(db)
            onUI {
                resetCache(fetchResult.map, fetchResult.top)
                log.i("fetchAllCache.onEnd")
                onEnd()
            }
        }
    }

    fun save(db: MediaDatabase, fileList: List<MediaRoot>, onEnd: () -> Unit) {
        val modeId = System.currentTimeMillis()
        currentModeId = modeId
        log.i("save.onStart, modeId = $modeId")
        doAsync {
            val allFileList = ArrayList<MediaInfo>()
            val pendingList = LinkedList<MediaInfo>()

            val tempMap = HashMap<String, MediaInfo.Directory>()
            val tempTop = ArrayList<MediaInfo>()
            fileList.forEach {
                pendingList.addAll(it.children)
            }
            while (pendingList.isNotEmpty()) {
                val first = pendingList.removeFirst()
                // 拍平，添加到集合里，它和外面的拍平不一样，这里需要保留文件夹
                allFileList.add(first)
                if (first is MediaInfo.Directory) {
                    // 添加到末尾
                    pendingList.addAll(first.children)
                    tempMap[first.docId] = first
                }
                if (first.parentDocId.isEmpty()) {
                    tempTop.add(first)
                }
            }
            log.i("save modeId = $modeId, allCount = ${allFileList.size}")
            val maxIndex = allFileList.size - 1
            var endIndex = 0
            val tempCache = CacheInfo()
            while (endIndex < maxIndex) {
                val startIndex = endIndex
                // 每次事务最多200条
                val newEnd = min(maxIndex, endIndex + 200)
                endIndex = newEnd
                // 在NewEnd的基础上再往后一位
                endIndex++
                db.updateCache { newLine ->
                    for (i in startIndex..newEnd) {
                        if (i <= maxIndex) {
                            val info = allFileList[i]

                            tempCache.docId = info.docId
                            tempCache.displayName = info.name
                            tempCache.mimeType = info.mimeType
                            tempCache.size = info.size
                            tempCache.parentId = info.parentDocId
                            tempCache.uri = info.uriString
                            tempCache.rootUri = info.rootUri.toString()
                            tempCache.lastModified = info.lastModified
                            tempCache.modeId = modeId
                            tempCache.filePath = info.path
                            if (info is MediaInfo.File) {
                                tempCache.mediaType = info.mediaType.dataKey
                            } else {
                                tempCache.mediaType = ""
                            }

                            newLine(tempCache)
                        }
                    }
                }
            }
            db.deleteCache(modeId)
            onUI {
                log.i("save.onEnd,  modeId = $modeId, count = ${allFileList.size}")
                resetCache(tempMap, tempTop)
                onEnd()
            }
        }
    }

    fun fetchAllCacheSync(db: MediaDatabase): FetchResult {
        val tempMap = HashMap<String, MediaInfo.Directory>()
        val tempTop = ArrayList<MediaInfo>()
        db.fillingCache { line ->
            val parentId = line.parentId
            val docId = line.docId
            val newInfo = if (line.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val tempDir = tempMap[docId]
                val newDir = MediaInfo.Directory(
                    uri = line.uri.toUri(),
                    name = line.displayName,
                    size = line.size,
                    lastModified = line.lastModified,
                    path = line.filePath,
                    rootUri = line.rootUri.toUri(),
                    docId = docId,
                    mimeType = line.mimeType,
                    parentDocId = line.parentId
                )
                tempDir?.let {
                    newDir.children.addAll(it.children)
                }
                // 更新目录索引为真正的对象
                tempMap[docId] = newDir
                newDir
            } else {
                MediaInfo.File(
                    uri = line.uri.toUri(),
                    name = line.displayName,
                    size = line.size,
                    lastModified = line.lastModified,
                    path = line.filePath,
                    rootUri = line.rootUri.toUri(),
                    docId = docId,
                    mimeType = line.mimeType,
                    parentDocId = line.parentId,
                    mediaType = MediaType.findByKey(line.mediaType) ?: MediaType.Image,
                )
            }
            if (parentId.isNotEmpty()) {
                val parent = tempMap[parentId] ?: makeEmptyDir(parentId)
                parent.children.add(newInfo)
                tempMap[parentId] = parent
            } else {
                tempTop.removeIf { it.docId == docId }
                tempTop.add(newInfo)
            }
        }
        return FetchResult(map = tempMap, top = tempTop)
    }

    private fun makeEmptyDir(docId: String): MediaInfo.Directory {
        return MediaInfo.Directory(
            docId = docId,
            uri = Uri.EMPTY,
            name = "",
            mimeType = "",
            size = 0,
            lastModified = 0,
            path = "",
            parentDocId = "",
            rootUri = Uri.EMPTY,
        )
    }

    class FetchResult(
        val map: Map<String, MediaInfo.Directory>,
        val top: List<MediaInfo>
    )

}