package com.lollipop.mediaflow.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.net.toUri
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.TaskResult
import com.lollipop.mediaflow.tools.Tasks
import com.lollipop.mediaflow.tools.delay
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.source.ArchiveSubject
import com.lollipop.mediaflow.data.source.MediaBackends
import com.lollipop.mediaflow.data.source.SourceId
import com.lollipop.mediaflow.tools.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.LinkedList

object ArchiveManager {

    private const val CONFIG_LIST = "config_list"
    private const val CONFIG_NAME = "config_name"
    private const val CONFIG_URI = "config_uri"

    private const val CONFIG_FAVORITE = "favorite"
    private const val CONFIG_SPECIAL = "special"
    private const val CONFIG_THUMP_UP = "thump_up"

    private val log by lazy {
        registerLog()
    }

    private val initStateImpl = mutableStateOf<InitState>(InitState.Pending)

    private val favoriteBasket = mutableStateOf<ArchiveBasket?>(null)
    private val specialBasket = mutableStateOf<ArchiveBasket?>(null)
    private val thumpUpBasket = mutableStateOf<ArchiveBasket?>(null)

    const val PROGRESS_INFINITE = -1F
    const val PROGRESS_COMPLETE = 1F

    val initState: State<InitState>
        get() {
            return initStateImpl
        }

    val favorite: State<ArchiveBasket?>
        get() {
            return favoriteBasket
        }

    val special: State<ArchiveBasket?>
        get() {
            return specialBasket
        }

    val thumpUp: State<ArchiveBasket?>
        get() {
            return thumpUpBasket
        }

    /**
     * 回收任务的列表
     */
    val archiveTaskList = mutableStateListOf<ArchiveTask>()

    /**
     * 历史任务的列表
     */
    val historyTaskList = mutableStateListOf<ArchiveTask>()

    /**
     * 回收站篮子的集合
     */
    val archiveBasketList = mutableStateListOf<ArchiveBasket>()

    /**
     * 待执行的回收任务
     */
    private val pendingList = LinkedList<ArchiveTask>()
    private var contextRef: WeakReference<Context>? = null

    private val config by lazy {
        ConfigHelper("archive_manager")
    }

    private val pendingSaveTask = Runnable {
        saveConfig()
    }

    /**
     * 就地重命名单个文件（归档重命名页使用）。
     *
     * 执行原语已迁至 [LocalArchiveBackend]，此处仅作对外转发，保证调用方签名不变。
     */
    suspend fun rename(
        resolver: ContentResolver,
        file: MediaInfo.File,
        newName: String
    ): TaskResult<Uri> {
        return LocalArchiveBackend.rename(resolver, file, newName)
    }

    /**
     * 去掉归档重命名前缀，还原原始文件名。
     *
     * 执行原语已迁至 [LocalArchiveBackend]，此处仅作对外转发。
     */
    fun restoreOriginalFileName(currentName: String): String {
        return LocalArchiveBackend.restoreOriginalFileName(currentName)
    }


    fun init(context: Context) {
        val app = context.applicationContext
        contextRef = WeakReference(app)
        if (initState.value == InitState.Successful) {
            return
        }
        initStateImpl.value = InitState.Loading
        Tasks.launch {
            config.load(context)
            onConfigLoaded(app, config.jsonConfig)
        }
    }

    /**
     * 获取篮子类型
     */
    fun getBasketType(basket: ArchiveBasket): ArchiveQuick {
        if (favoriteBasket.value?.uriString == basket.uriString) {
            return ArchiveQuick.Favorite
        }
        if (specialBasket.value?.uriString == basket.uriString) {
            return ArchiveQuick.Special
        }
        if (thumpUpBasket.value?.uriString == basket.uriString) {
            return ArchiveQuick.ThumpUp
        }
        return ArchiveQuick.Other
    }

    fun isQuickEnable(type: ArchiveQuick): Boolean {
        if (initState.value != InitState.Successful) {
            return false
        }
        if (!Preferences.isQuickArchiveEnable.get()) {
            return false
        }
        when (type) {
            ArchiveQuick.Favorite -> {
                return favoriteBasket.value != null
            }

            ArchiveQuick.Special -> {
                return specialBasket.value != null
            }

            ArchiveQuick.ThumpUp -> {
                return thumpUpBasket.value != null
            }

            ArchiveQuick.Other -> {
                if (!Preferences.isShowOtherQuickArchiveButton.get()) {
                    return false
                }
                return archiveBasketList.isNotEmpty()
            }
        }
    }

    fun addBasket(context: Context, name: String, uri: Uri) {
        updateContext(context)
        archiveBasketList.add(ArchiveBasket(sourceId = SourceId.Local, name = name, uri = uri))
        pendingSaveTask.delay(500)
    }

    fun removeBasket(context: Context, basket: ArchiveBasket) {
        updateContext(context)
        val uriString = basket.uriString
        archiveBasketList.removeIf { it.uriString == uriString }
        if (favoriteBasket.value?.uriString == uriString) {
            favoriteBasket.value = null
        }
        if (specialBasket.value?.uriString == uriString) {
            specialBasket.value = null
        }
        if (thumpUpBasket.value?.uriString == uriString) {
            thumpUpBasket.value = null
        }
        pendingSaveTask.delay(500)
    }

    fun setQuick(context: Context, type: ArchiveQuick, basket: ArchiveBasket) {
        updateContext(context)
        // 需要先移除原来的
        favoriteBasket.value?.let {
            if (it.uriString == basket.uriString) {
                favoriteBasket.value = null
            }
        }
        specialBasket.value?.let {
            if (it.uriString == basket.uriString) {
                specialBasket.value = null
            }
        }
        thumpUpBasket.value?.let {
            if (it.uriString == basket.uriString) {
                thumpUpBasket.value = null
            }
        }

        when (type) {
            ArchiveQuick.Favorite -> {
                favoriteBasket.value = basket
            }

            ArchiveQuick.Special -> {
                specialBasket.value = basket
            }

            ArchiveQuick.ThumpUp -> {
                thumpUpBasket.value = basket
            }

            ArchiveQuick.Other -> {
                // 这里就是好不设置的意思
            }
        }
        pendingSaveTask.delay(500)
    }

    private fun updateContext(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    private fun saveConfig() {
        Tasks.launch(Dispatchers.IO) {
            val uriMap = HashMap<String, ArchiveBasket>()

            archiveBasketList.forEach {
                uriMap[it.uriString] = it
            }

            val newArray = JSONArray()

            uriMap.values.forEach { info ->
                newArray.put(
                    JSONObject().also {
                        it.put(CONFIG_NAME, info.name)
                        it.put(CONFIG_URI, info.uri.toString())
                    }
                )
            }

            val jsonConfig = config.jsonConfig
            jsonConfig.remove(CONFIG_LIST)
            jsonConfig.put(CONFIG_LIST, newArray)
            jsonConfig.put(CONFIG_FAVORITE, favoriteBasket.value?.uriString ?: "")
            jsonConfig.put(CONFIG_SPECIAL, specialBasket.value?.uriString ?: "")
            jsonConfig.put(CONFIG_THUMP_UP, thumpUpBasket.value?.uriString ?: "")

            config.save(contextRef?.get())
        }
    }

    private suspend fun onConfigLoaded(app: Context, jsonConfig: JSONObject) {
        withContext(Dispatchers.IO) {
            val tempList = ArrayList<ArchiveBasket>()
            val array = jsonConfig.optJSONArray(CONFIG_LIST)
            if (array != null) {
                val length = array.length()
                for (i in 0 until length) {
                    try {
                        val obj = array.optJSONObject(i)
                        val name = obj.optString(CONFIG_NAME)
                        val uriValue = obj.optString(CONFIG_URI)
                        if (uriValue.isNotEmpty()) {
                            val uri = uriValue.toUri()
                            if (uri != Uri.EMPTY) {
                                if (MediaChooser.hasWritePermission(app, uriValue)) {
                                    tempList.add(
                                        ArchiveBasket(
                                            sourceId = SourceId.Local,
                                            name = name,
                                            uri = uri
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        log.e("onConfigLoaded", e)
                    }
                }
            }
            val favoriteUriString = jsonConfig.optString(CONFIG_FAVORITE)
            val specialUriString = jsonConfig.optString(CONFIG_SPECIAL)
            val thumpUpUriString = jsonConfig.optString(CONFIG_THUMP_UP)
            var favorite: ArchiveBasket? = null
            var special: ArchiveBasket? = null
            var thumpUp: ArchiveBasket? = null
            tempList.forEach {
                if (it.uriString == favoriteUriString) {
                    favorite = it
                }
                if (it.uriString == specialUriString) {
                    special = it
                }
                if (it.uriString == thumpUpUriString) {
                    thumpUp = it
                }
                LocalArchiveBackend.checkNoMediaFile(app, it.docUri)
            }
            withContext(Dispatchers.Main) {
                Snapshot.withMutableSnapshot {
                    archiveBasketList.clear()
                    archiveBasketList.addAll(tempList)
                }
                favoriteBasket.value = favorite
                specialBasket.value = special
                thumpUpBasket.value = thumpUp
                initStateImpl.value = if (archiveBasketList.isEmpty()) {
                    InitState.NoneDir
                } else {
                    InitState.Successful
                }
            }
            flushPending(app)
        }
    }

    /**
     * 归档编排入口：只接收「谁（来源）+ 把什么（**已锁定的句柄**）+ 放哪里（归档点）」，负责
     * 去重、建任务、排队后台搬运；**不接触 `LMedia`、不触发前台移除**。
     *
     * 锁定（`prepare`）与前台移除由入口 [com.lollipop.mediaflow.tools.ArchiveHelper] 在**调用本方法之前**
     * 按序完成（见其 KDoc）；本方法拿到的是已锚定的 [ArchiveSubject]。
     *
     * @return 任务句柄；重复在途返回既有任务。
     */
    fun archive(
        context: Context,
        sourceId: SourceId,
        subject: ArchiveSubject,
        basket: ArchiveBasket
    ): ArchiveTask? {
        contextRef = WeakReference(context)

        // 去重：同来源 + 同 tag（判定维度由来源内部决定）在途则复用既有任务
        try {
            archiveTaskList.find {
                it.sourceId == sourceId && it.subject.tag == subject.tag
            }?.let {
                return it
            }
            pendingList.find {
                it.sourceId == sourceId && it.subject.tag == subject.tag
            }?.let {
                return it
            }
        } catch (e: Throwable) {
            log.e("archive", e)
            return null
        }

        val taskInfo = ArchiveTask(sourceId = sourceId, subject = subject, basket = basket)

        if (initState.value == InitState.Loading) {
            pendingList.add(taskInfo)
            return taskInfo
        }
        if (initState.value == InitState.Pending) {
            pendingList.add(taskInfo)
            init(context)
            return taskInfo
        }
        Tasks.launch {
            // 先清一下待执行队列
            flushPending(context)
            // 再搬当前这条
            doMove(context, taskInfo)
        }
        return taskInfo
    }

    private suspend fun flushPending(context: Context) {
        while (pendingList.isNotEmpty()) {
            val archiveTask = pendingList.removeFirst()
            doMove(context, archiveTask)
        }
    }

    /**
     * 执行一次归档任务：只做**编排**（登记任务 / 进度 / 历史），实际搬运交给
     * 该来源的 [com.lollipop.mediaflow.data.source.ArchiveBackend]。协议细节不外泄到编排层。
     */
    private suspend fun doMove(context: Context, taskInfo: ArchiveTask) {
        withContext(Dispatchers.IO) {
            archiveTaskList.add(taskInfo)

            taskInfo.progressState = PROGRESS_INFINITE
            MediaBackends.archiver(taskInfo.sourceId)?.archive(
                context = context,
                subject = taskInfo.subject,
                basket = taskInfo.basket,
                onProgress = { taskInfo.progressState = it }
            )

            taskInfo.progressState = PROGRESS_COMPLETE

            archiveTaskList.remove(taskInfo)
            historyTaskList.add(taskInfo)
        }
    }

    enum class InitState {

        Pending,
        Loading,
        NoneDir,
        Successful,
        Error

    }

    /**
     * 归档任务：**谁 - 把什么 - 放哪里**。
     *
     * - 谁：[sourceId]（来源）；
     * - 把什么：[subject]（[com.lollipop.mediaflow.data.source.ArchiveBackend.prepare] 产出的
     *   可搬运句柄；去重用 [ArchiveSubject.tag]，展示用 [ArchiveSubject.description]）；
     * - 放哪里：[basket]（归档点）。
     *
     * **不含任何协议细节**（SAF、路径、凭据等留在该来源的 `ArchiveBackend` 内部）。
     */
    class ArchiveTask(
        val sourceId: SourceId,
        val subject: ArchiveSubject,
        val basket: ArchiveBasket
    ) {

        var progressState by mutableFloatStateOf(PROGRESS_INFINITE)

    }

}

enum class ArchiveQuick(
    val iconRes: Int,
    val labelRes: Int
) {
    Favorite(iconRes = R.drawable.favorite_24, labelRes = R.string.label_quick_archive_favorite),
    Special(iconRes = R.drawable.star_24, labelRes = R.string.label_quick_archive_special),
    ThumpUp(iconRes = R.drawable.thumb_up_24, labelRes = R.string.label_quick_archive_thump_up),
    Other(iconRes = R.drawable.archive_24, labelRes = R.string.label_quick_archive_other)
}

class ArchiveBasket(
    /** 归档点所属来源；装配时贴上，本地记录一律为 [SourceId.Local]。 */
    val sourceId: SourceId,
    val name: String,
    val uri: Uri
) {

    val uriString: String by lazy {
        uri.toString()
    }

    /**
     * 运行期标识：来源 + 地址。
     *
     * 只在内存里用（槽位比对、去重），**不落盘**——落盘仍是裸 uri 字符串。
     */
    val key: String by lazy {
        "${sourceId.key}|$uriString"
    }

    val uriPath: String by lazy {
        uri.path ?: ""
    }

    val docId: String by lazy {
        DocumentsContract.getTreeDocumentId(uri)
    }

    val docUri: Uri by lazy {
        DocumentsContract.buildDocumentUriUsingTree(uri, docId)
    }

}