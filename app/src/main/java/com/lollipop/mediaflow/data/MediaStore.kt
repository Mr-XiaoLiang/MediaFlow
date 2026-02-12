package com.lollipop.mediaflow.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.doAsync
import com.lollipop.mediaflow.tools.onUI
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class MediaStore private constructor(
    val cache: StoreCache,
    val context: Context,
    val visibility: MediaVisibility
) {

    companion object {
        private val cacheMap = ConcurrentHashMap<MediaVisibility, StoreCache>()
        private val galleryCache = CopyOnWriteArrayList<Gallery>()

        fun loadStore(context: Context, visibility: MediaVisibility): MediaStore {
            val cache = cacheMap.computeIfAbsent(visibility) {
                StoreCache(visibility)
            }
            return MediaStore(cache, context, visibility)
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

        fun createListener(
            lifecycleOwner: LifecycleOwner,
            listener: OnDataChangedListener
        ): LifecycleDataChangedListener {
            return LifecycleDataChangedListener(
                lifecycleOwner = lifecycleOwner,
                outListener = listener
            )
        }

    }

    val key: String = visibility.key

    private val mediaDatabase by lazy {
        MediaDatabase(context)
    }

    private val log = registerLog()

    private val requestList = CopyOnWriteArrayList<LoadCallback>()
    private val dataChangedListener = CopyOnWriteArrayList<OnDataChangedListener>()

    var dataVersion = 1L
        private set

    var isLoading = false
        private set

    fun register(listener: OnDataChangedListener) {
        this.dataChangedListener.add(listener)
    }

    fun unregister(listener: OnDataChangedListener) {
        this.dataChangedListener.remove(listener)
    }

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
        synchronized(this) {
            loadInner(isRefresh = isRefresh, onComplete = onComplete)
        }
    }

    private fun updateDataVersion() {
        dataVersion++
        if (dataVersion == Long.MAX_VALUE) {
            dataVersion = Long.MIN_VALUE
        }
        log.i("updateDataVersion dataVersion = $dataVersion")
    }

    private fun notifyDataChanged() {
        log.i("notifyDataChanged dataVersion = $dataVersion")
        dataChangedListener.forEach {
            onUI {
                it.onDataChanged(this)
            }
        }
    }

    private fun loadInner(isRefresh: Boolean, onComplete: LoadCallback) {
        log.i("loadInner isRefresh = $isRefresh")
        requestList.add(onComplete)
        if (isLoading) {
            log.i("loadInner isLoading = true, break")
            return
        }
        isLoading = true
        doAsync(
            error = {
                log.e("loadInner 加载根目录失败", it)
                onUI {
                    dispatchLoadResult(false)
                }
            }
        ) {
            log.i("loadInner doAsync begin")
            loadRootSync(isRefresh = isRefresh)
            val fileList = mutableListOf<MediaRoot>()
            val directoryTree = mutableListOf<MediaDirectoryTree>()
            if (!isRefresh) {
                val localResult = LocalMediaProvider.fetchAllCacheSync(
                    visibility = visibility,
                    db = MediaLoader.getMediaDatabase(context)
                )
                cache.rootList.forEach {
                    val rootChildren = ArrayList<MediaInfo>()
                    val rootName = it.name
                    localResult.top.forEach { top ->
                        log.d("loadInner local root: ${top.path}")
                        if (top.path == rootName) {
                            rootChildren.add(top)
                        }
                    }
                    val root = MediaRoot(it.name, rootChildren)
                    log.d("loadInner local root ${root.name}, ${root.children.size}")
                    fileList.add(root)
                    directoryTree.add(loadDirectoryTree(root))
                }
                log.i("loadInner doAsync localResult = ${fileList.size}, top = ${localResult.top.size}")
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
                    visibility = visibility,
                    db = MediaLoader.getMediaDatabase(context),
                    fileList = fileList
                ) {
                    log.i("loadInner isRefresh = $isRefresh, save complete ")
                }
            }
            updateDataVersion()
            onUI {
                log.i("loadInner onUI begin")
                isLoading = false
                dispatchLoadResult(true)
                log.i("loadInner onUI end")
            }
            notifyDataChanged()
            log.i("loadInner doAsync end, data count = ${fileList.size}")
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
            loadRootSync(true)
            onUI {
                onComplete.invoke(true)
            }
        }
    }

    private fun loadRootSync(isRefresh: Boolean) {
        val rootUri = mediaDatabase.loadRootUri(visibility = cache.visibility)
        if (!isRefresh) {
            cache.resetRoots(rootUri)
            return
        }
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
        val store: MediaStore,
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

        fun refresh(sort: MediaSort, onComplete: GalleryCallback) {
            log.i("refresh sort = $sort")
            this.sortType = sort
            galleryCallback.add(onComplete)
            store.fetch(isRefresh = true, loadCallback)
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
                    loadAll(tempList, allFile)
                    if (allFile.isEmpty()) {
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

    }

    fun interface GalleryCallback {
        fun onGalleryLoaded(gallery: Gallery, success: Boolean)
    }

    fun interface LoadCallback {
        fun onLoaded(success: Boolean)
    }

    fun interface OnDataChangedListener {
        fun onDataChanged(store: MediaStore)
    }

    class LifecycleDataChangedListener(
        val lifecycleOwner: LifecycleOwner,
        val outListener: OnDataChangedListener
    ) : OnDataChangedListener {

        private var isPause = true
        private val storeList = HashMap<String, MediaStore>()
        private val pendingChangedStore = HashSet<String>()
        private val dataVersionMap = HashMap<String, Long>()

        init {
            lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
                val observer = this
                override fun onStateChanged(
                    source: LifecycleOwner,
                    event: Lifecycle.Event
                ) {
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            isPause = false
                            onResume()
                        }

                        Lifecycle.Event.ON_PAUSE -> {
                            isPause = true
                            onPause()
                        }

                        Lifecycle.Event.ON_DESTROY -> {
                            isPause = true
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            onDestroy()
                        }

                        else -> {}
                    }
                }
            })
        }

        fun currentDataVersion(store: MediaStore): Long {
            return dataVersionMap[store.key] ?: 0L
        }

        fun register(vararg store: MediaStore) {
            store.forEach {
                if (!storeList.containsKey(it.key)) {
                    it.register(this)
                    storeList[it.key] = it
                }
            }
        }

        private fun onResume() {
            pendingChangedStore.forEach { key ->
                storeList[key]?.let {
                    dispatchChanged(it)
                }
            }
            pendingChangedStore.clear()
        }

        private fun onPause() {

        }

        private fun onDestroy() {
            storeList.values.forEach {
                it.unregister(this)
            }
            storeList.clear()
        }

        override fun onDataChanged(store: MediaStore) {
            if (isPause) {
                pendingChangedStore.add(store.key)
            } else {
                dispatchChanged(store)
            }
        }

        private fun dispatchChanged(store: MediaStore) {
            val key = store.key
            val currentVersion = dataVersionMap[key]
            val newVersion = store.dataVersion
            if (currentVersion != newVersion) {
                dataVersionMap[key] = newVersion
                outListener.onDataChanged(store)
            }
        }

    }

}