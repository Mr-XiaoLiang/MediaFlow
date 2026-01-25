package com.lollipop.mediaflow.data

import android.content.Context
import android.net.Uri
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.doAsync
import com.lollipop.mediaflow.tools.onUI
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class MediaStore private constructor(
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

        fun loadGallery(
            context: Context,
            visibility: MediaVisibility,
            mediaType: MediaType
        ): Gallery {
            return Gallery(
                context = context,
                store = loadStore(context, visibility),
                mediaType = mediaType,
            )
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
            log.i("add: 添加根目录成功: $uri, cache.size = ${cache.rootList.size}")
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
            mediaDatabase.deleteRootUri(uriString, cache.visibility)
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
            loadRootSync()
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

    fun loadRootUri(onComplete: (Boolean) -> Unit) {
        doAsync(
            error = {
                log.e("loadRootUri: 加载根目录失败", it)
                onUI {
                    onComplete.invoke(false)
                }
            }
        ) {
            loadRootSync()
            onUI {
                onComplete.invoke(true)
            }
        }
    }

    private fun loadRootSync() {
        val rootUri = mediaDatabase.loadRootUri(visibility = cache.visibility)
        val uriSet = rootUri.map { it.uri }.toSet()
        val validUri = MediaChooser.findPermissionValid(context, uriSet)
        log.i("loadRootSync: 加载根目录成功: ${rootUri.size}, visibility = ${cache.visibility.key}")
        if (validUri.size != uriSet.size) {
            log.w("load: 部分URI权限无效")
            val newList = rootUri.filter { it.uri in validUri }
            cache.resetRoots(newList)
        } else {
            cache.resetRoots(rootUri)
            log.i("loadRootSync: 所有URI权限有效")
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

    class Gallery(
        private val context: Context,
        private val store: MediaStore,
        private val mediaType: MediaType,
    ) {

        val dataList = ArrayList<MediaRoot>()

        val fileList = ArrayList<MediaInfo.File>()

        fun load(sort: MediaSort, onComplete: (Boolean) -> Unit) {
            doAsync {
                val tempList = ArrayList<MediaRoot>()
                tempList.addAll(store.cache.fileList)
                val allFile = ArrayList<MediaInfo.File>()
                val pending = LinkedList<MediaInfo>()
                tempList.forEach {
                    pending.addAll(it.children)
                }
                while (pending.isNotEmpty()) {
                    val item = pending.removeFirst()
                    if (item is MediaInfo.File) {
                        if (item.mediaType == mediaType) {
                            allFile.add(item)
                        }
                        continue
                    }
                    if (item is MediaInfo.Directory) {
                        item.children.forEach { child ->
                            if (child is MediaInfo.File) {
                                if (child.mediaType == mediaType) {
                                    allFile.add(child)
                                }
                            } else if (child is MediaInfo.Directory) {
                                pending.add(child)
                            }
                        }
                    }
                }
                sort.sort(allFile)
                onUI {
                    dataList.clear()
                    dataList.addAll(tempList)
                    fileList.clear()
                    fileList.addAll(allFile)
                    onComplete.invoke(true)
                }
            }
        }

        fun refresh(sort: MediaSort, onComplete: (Boolean) -> Unit) {
            store.load {
                load(sort, onComplete)
            }
        }

    }

}