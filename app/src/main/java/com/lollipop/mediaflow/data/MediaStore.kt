package com.lollipop.mediaflow.data

import android.content.Context
import android.net.Uri
import com.lollipop.mediaflow.LApplication
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.doAsync
import com.lollipop.mediaflow.tools.onUI
import com.lollipop.mediaflow.tools.task
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class MediaStore private constructor(
    val cache: StoreCache,
    val context: Context
) {

    companion object {
        private val cacheMap = ConcurrentHashMap<MediaVisibility, StoreCache>()
        private val galleryCache = CopyOnWriteArrayList<Gallery>()

        private const val MIN_LOAD_DELAY = 1000

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
            synchronized(galleryCache) {
                galleryCache.forEach {
                    if (it.mediaType == mediaType && it.visibility == visibility) {
                        return it
                    }
                }
                val newGallery = Gallery(
                    store = loadStore(context, visibility),
                    mediaType = mediaType,
                    visibility = visibility
                )
                galleryCache.add(newGallery)
                return newGallery
            }
        }

    }

    private val mediaDatabase by lazy {
        MediaDatabase(context)
    }

    private val log = registerLog()

    private val requestList = CopyOnWriteArrayList<LoadCallback>()

    var isLoading = false
        private set

    fun add(uri: Uri, onComplete: (Boolean) -> Unit) {
        doAsync(
            error = {
                log.e("add", it)
                onUI {
                    onComplete.invoke(false)
                }
            }
        ) {
            val name = MediaLoader.getRootFolderName(context, uri)
            val rootUri = RootUri(uri = uri, visibility = cache.visibility, name = name ?: "")
            cache.addRoot(rootUri)
            mediaDatabase.saveRootUri(rootUri)
            log.i("add uri = $uri, cache.size = ${cache.rootList.size}")
            onUI {
                onComplete.invoke(true)
            }
        }
    }

    fun remove(uri: Uri, onComplete: (Boolean) -> Unit) {
        doAsync(
            error = {
                log.e("remove 删除根目录失败", it)
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

    fun fetch(isRefresh: Boolean, onComplete: LoadCallback) {
        val now = System.currentTimeMillis()
        val activeTime = now - LApplication.launchTime
        if (activeTime < MIN_LOAD_DELAY) {
            task {
                loadInner(isRefresh = isRefresh, onComplete = onComplete)
            }.delay(MIN_LOAD_DELAY - activeTime)
        } else {
            loadInner(isRefresh = isRefresh, onComplete = onComplete)
        }
    }

    private fun loadInner(isRefresh: Boolean, onComplete: LoadCallback) {
        log.i("load isRefresh = $isRefresh")
        requestList.add(onComplete)
        if (isLoading) {
            log.i("load isLoading = true, break")
            return
        }
        isLoading = true
        doAsync(
            error = {
                log.e("load: 加载根目录失败", it)
                onUI {
                    dispatchLoadResult(false)
                }
            }
        ) {
            log.i("load doAsync begin")
            loadRootSync()
            val fileList = mutableListOf<MediaRoot>()
            val directoryTree = mutableListOf<MediaDirectoryTree>()
            if (!isRefresh) {
                val localResult = LocalMediaProvider.fetchAllCacheSync(
                    MediaLoader.getMediaDatabase(context)
                )
                cache.rootList.forEach {
                    val rootChildren = ArrayList<MediaInfo>()
                    val uriString = it.uriString
                    localResult.top.forEach { top ->
                        if (top.rootUri.toString() == uriString) {
                            rootChildren.add(top)
                        }
                    }
                    val root = MediaRoot(it.name, rootChildren)
                    fileList.add(root)
                    directoryTree.add(loadDirectoryTree(root))
                }
                log.i("load doAsync localResult = ${fileList.size}, top = ${localResult.top.size}")
            }
            if (fileList.isNotEmpty()) {
                cache.resetFiles(fileList)
                cache.resetDirectoryTree(directoryTree)
            }

            if (isRefresh || fileList.isEmpty()) {
                // 刷新需要从媒体库直接更新
                cache.rootList.forEach {
                    val mediaRoot = MediaLoader.loadTreeSync(context, it.uri, it.name)
                    fileList.add(mediaRoot)
                    directoryTree.add(loadDirectoryTree(mediaRoot))
                }
                cache.resetFiles(fileList)
                cache.resetDirectoryTree(directoryTree)
                LocalMediaProvider.save(
                    MediaLoader.getMediaDatabase(context),
                    fileList
                ) {
                    log.i("load：isRefresh = $isRefresh, save complete ")
                }
            }
            onUI {
                log.i("load onUI begin")
                isLoading = false
                dispatchLoadResult(true)
                log.i("load onUI end")
            }
            log.i("load doAsync end, data count = ${fileList.size}")
        }
    }

    private fun loadDirectoryTree(mediaRoot: MediaRoot): MediaDirectoryTree {
        val root = MediaDirectoryTree.Root(mediaRoot)
        val pending = LinkedList<MediaDirectoryTree>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val treeParent = pending.removeFirst()
            if (treeParent is MediaDirectoryTree.Root) {
                treeParent.current.children.forEach { media ->
                    if (media is MediaInfo.Directory) {
                        val directory = MediaDirectoryTree.Directory(media, treeParent)
                        treeParent.children.add(directory)
                        pending.add(directory)
                    }
                }
            } else if (treeParent is MediaDirectoryTree.Directory) {
                treeParent.current.children.forEach { media ->
                    if (media is MediaInfo.Directory) {
                        val directory = MediaDirectoryTree.Directory(media, treeParent)
                        treeParent.children.add(directory)
                        pending.add(directory)
                    }
                }
            }
        }
        root.calculateFileCount()
        return root
    }

    private fun dispatchLoadResult(success: Boolean) {
        val tempList = mutableListOf<LoadCallback>()
        tempList.addAll(requestList)
        requestList.clear()
        tempList.forEach {
            it.onLoaded(success)
        }
    }

    fun loadRootUri(onComplete: (Boolean) -> Unit) {
        doAsync(
            error = {
                log.e("loadRootUri 加载根目录失败", it)
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
        log.i("loadRootSync 加载根目录成功: ${rootUri.size}, visibility = ${cache.visibility.key}")
        if (validUri.size != uriSet.size) {
            log.w("load 部分URI权限无效")
            val newList = rootUri.filter { it.uri in validUri }
            cache.resetRoots(newList)
        } else {
            cache.resetRoots(rootUri)
            log.i("loadRootSync 所有URI权限有效")
        }
    }

    class StoreCache(val visibility: MediaVisibility) {
        private val rootUriList = CopyOnWriteArrayList<RootUri>()
        private val rootUriMap = ConcurrentHashMap<String, RootUri>()
        private val allFileList = CopyOnWriteArrayList<MediaRoot>()

        private val directoryTree = CopyOnWriteArrayList<MediaDirectoryTree>()

        val rootList: List<RootUri>
            get() = rootUriList

        val fileList: List<MediaRoot>
            get() = allFileList

        val treeList: List<MediaDirectoryTree>
            get() = directoryTree

        fun resetFiles(rootUri: List<MediaRoot>) {
            allFileList.clear()
            allFileList.addAll(rootUri)
        }

        fun resetDirectoryTree(tree: List<MediaDirectoryTree>) {
            directoryTree.clear()
            directoryTree.addAll(tree)
        }

        fun addRoot(rootUri: RootUri) {
            val old = rootUriMap[rootUri.uriString]
            if (old != null) {
                rootUriList.remove(old)
            }
            rootUriMap[rootUri.uriString] = rootUri
            rootUriList.add(rootUri)
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
        private val store: MediaStore,
        val mediaType: MediaType,
        val visibility: MediaVisibility
    ) {

        val directoryTree = ArrayList<MediaDirectoryTree>()

        val fileList = ArrayList<MediaInfo.File>()

        var rootDirectory: MediaDirectoryTree? = null
            private set

        private val log by lazy {
            registerLog()
        }

        var sortType: MediaSort = MediaSort.DateDesc
            private set

        private val galleryCallback = LinkedList<GalleryCallback>()

        private val loadCallback = LoadCallback {
            loadData()
        }

        fun setRootDirectory(directory: MediaDirectoryTree?) {
            rootDirectory = directory
        }

        fun load(sort: MediaSort = sortType, onComplete: GalleryCallback) {
            log.i("load sort = $sort")
            galleryCallback.add(onComplete)
            this.sortType = sort
            loadData()
        }

        private fun loadAll(pending: LinkedList<MediaInfo>, out: MutableList<MediaInfo.File>) {
            log.i("loadAll pending.size = ${pending.size}")
            while (pending.isNotEmpty()) {
                val item = pending.removeFirst()
                if (item is MediaInfo.File) {
                    if (item.mediaType == mediaType) {
                        out.add(item)
                    }
                    continue
                }
                if (item is MediaInfo.Directory) {
                    item.children.forEach { child ->
                        if (child is MediaInfo.File) {
                            if (child.mediaType == mediaType) {
                                out.add(child)
                            }
                        } else if (child is MediaInfo.Directory) {
                            pending.add(child)
                        }
                    }
                }
            }
        }

        private fun loadFromDirectory(
            dir: MediaDirectoryTree,
            out: MutableList<MediaInfo.File>
        ) {
            log.i("loadFromDirectory dir = ${dir.name}")
            val pending = LinkedList<MediaInfo>()
            if (dir is MediaDirectoryTree.Root) {
                pending.addAll(dir.current.children)
            } else if (dir is MediaDirectoryTree.Directory) {
                pending.addAll(dir.current.children)
            }
            loadAll(pending, out)
        }

        private fun loadAll(rootList: List<MediaRoot>, out: MutableList<MediaInfo.File>) {
            log.i("loadAll rootList.size = ${rootList.size}")
            val pending = LinkedList<MediaInfo>()
            rootList.forEach {
                pending.addAll(it.children)
            }
            loadAll(pending, out)
        }

        private fun loadData() {
            log.i("loadData")
            val dirTree = rootDirectory
            doAsync {
                log.i("loadData.doAsync")
                val tempTree = ArrayList<MediaDirectoryTree>()
                tempTree.addAll(store.cache.treeList)

                val allFile = ArrayList<MediaInfo.File>()
                if (dirTree != null) {
                    loadFromDirectory(dirTree, allFile)
                } else {
                    val tempList = ArrayList<MediaRoot>()
                    tempList.addAll(store.cache.fileList)
                    if (tempList.isEmpty()) {
                        log.i("loadData.doAsync tempList is Empty, store.load from local")
                        store.fetch(isRefresh = false) {
                            tempList.addAll(store.cache.fileList)
                            tempTree.clear()
                            tempTree.addAll(store.cache.treeList)
                            log.i("loadData.doAsync store.load from local result, tempList.size = ${tempList.size}")
                            loadAll(tempList, allFile)
                            log.i("loadData.doAsync allFile.size = ${allFile.size}")
                            sortType.sort(allFile)
                            onUI {
                                fileList.clear()
                                fileList.addAll(allFile)
                                directoryTree.clear()
                                directoryTree.addAll(tempTree)
                                notifyComplete(true)
                            }
                        }
                    } else {
                        log.i("loadData.doAsync tempList to result, allFile.size = ${allFile.size}")
                        loadAll(tempList, allFile)
                        sortType.sort(allFile)
                        onUI {
                            fileList.clear()
                            fileList.addAll(allFile)
                            directoryTree.clear()
                            directoryTree.addAll(tempTree)
                            notifyComplete(true)
                        }
                    }
                }
            }
        }

        private fun notifyComplete(success: Boolean) {
            log.i("notifyComplete success = $success, fileList.size = ${fileList.size}")
            while (galleryCallback.isNotEmpty()) {
                galleryCallback.removeFirst().onGalleryLoaded(this, success)
            }
        }

        fun refresh(sort: MediaSort, onComplete: GalleryCallback) {
            log.i("refresh sort = $sort")
            this.sortType = sort
            galleryCallback.add(onComplete)
            store.fetch(isRefresh = true, loadCallback)
        }

    }

    fun interface GalleryCallback {
        fun onGalleryLoaded(gallery: Gallery, success: Boolean)
    }

    fun interface LoadCallback {
        fun onLoaded(success: Boolean)
    }

}