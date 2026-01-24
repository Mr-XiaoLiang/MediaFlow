package com.lollipop.mediaflow.data

import android.content.Context
import android.net.Uri
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.doAsync
import com.lollipop.mediaflow.tools.onUI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class MediaStore(
    val cache: StoreCache,
    val context: Context
) {

    companion object {
        private val cacheMap = ConcurrentHashMap<MediaVisibility, StoreCache>()

        fun loadStore(context: Context, visibility: MediaVisibility): MediaStore {
            val cache = cacheMap.computeIfAbsent(visibility) {
                StoreCache(visibility)
            }
            return MediaStore(cache, context)
        }

    }

    private val mediaDatabase by lazy {
        MediaDatabase(context)
    }

    private val log = registerLog()

    fun add(uri: Uri, onComplete: (Boolean) -> Unit) {
        doAsync(
            error = {
                log.e("add: 添加根目录失败", it)
                onUI {
                    onComplete.invoke(false)
                }
            }
        ) {
            val name = MediaLoader.getRootFolderName(context, uri)
            val rootUri = RootUri(uri = uri, visibility = cache.visibility, name = name ?: "")
            cache.addRoot(rootUri)
            mediaDatabase.saveRootUri(rootUri)
            onUI {
                onComplete.invoke(true)
            }
        }
    }

    fun remove(uri: Uri, onComplete: (Boolean) -> Unit) {
        doAsync(
            error = {
                log.e("remove: 删除根目录失败", it)
                onUI {
                    onComplete.invoke(false)
                }
            }
        ) {
            val uriString = uri.toString()

            cache.removeRoot(uriString)
            mediaDatabase.deleteRootUri(uriString)
            onUI {
                onComplete.invoke(true)
            }
        }
    }

    fun load(onComplete: (Boolean) -> Unit) {
        doAsync(
            error = {
                log.e("load: 加载根目录失败", it)
                onUI {
                    onComplete.invoke(false)
                }
            }
        ) {
            val rootUri = mediaDatabase.loadRootUri(visibility = cache.visibility)
            val uriSet = rootUri.map { it.uri }.toSet()
            val validUri = MediaChooser.findPermissionValid(context, uriSet)
            if (validUri.size != uriSet.size) {
                log.w("load: 部分URI权限无效")
                val newList = rootUri.filter { it.uri in validUri }
                cache.resetRoots(newList)
            }
            val fileList = mutableListOf<MediaRoot>()
            cache.rootList.forEach {
                val mediaRoot = MediaLoader.loadTreeSync(context, it.uri, it.name)
                fileList.add(mediaRoot)
            }
            cache.resetFiles(fileList)
            onUI {
                onComplete.invoke(true)
            }
        }
    }


    class StoreCache(val visibility: MediaVisibility) {
        private val rootUriList = CopyOnWriteArrayList<RootUri>()
        private val rootUriMap = ConcurrentHashMap<String, RootUri>()
        private val allFileList = CopyOnWriteArrayList<MediaRoot>()

        val rootList: List<RootUri>
            get() = rootUriList

        val fileList: List<MediaRoot>
            get() = allFileList

        fun addFiles(files: List<MediaRoot>) {
            allFileList.addAll(files)
        }

        fun clearFiles() {
            allFileList.clear()
        }

        fun resetFiles(rootUri: List<MediaRoot>) {
            allFileList.clear()
            allFileList.addAll(rootUri)
        }

        fun addRoot(rootUri: RootUri) {
            val old = rootUriMap[rootUri.uriString]
            if (old != null) {
                rootUriList.remove(old)
            }
            rootUriMap[rootUri.uriString] = rootUri
            rootUriList.add(rootUri)
        }

        fun removeRoot(rootUri: RootUri) {
            rootUriList.remove(rootUri)
            val remove = rootUriMap.remove(rootUri.uriString)
            if (remove != null) {
                rootUriList.remove(remove)
            }
        }

        fun removeRoot(uriString: String) {
            val remove = rootUriMap.remove(uriString)
            if (remove != null) {
                rootUriList.remove(remove)
            }
        }

        fun resetRoots(rootUri: List<RootUri>) {
            rootUriList.clear()
            rootUriList.addAll(rootUri)
            rootUriMap.clear()
            rootUri.forEach {
                rootUriMap[it.uriString] = it
            }
        }

    }

}