package com.lollipop.mediaflow.data.local

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.lollipop.mediaflow.data.SourceState
import com.lollipop.mediaflow.data.common.MediaSort
import com.lollipop.mediaflow.tools.Preferences

/**
 * Local 专属的业务状态实现，实现了 [SourceState] 统一契约。
 *
 * 每个单例都自带它对应的 (visibility, mediaType)，
 * 因此一次加载请求只需把「某个模式实例」（如 [PublicVideo]）作为 [SourceState] 传入，
 * 控制层即可反解出要刷新的范围与要填充的列表，无需再查表。
 *
 * 注：范围筛选 [scopeId] 对应 Gallery 的 rootDirectoryId（choose 范围），
 * Local 解释为「只展示某文件夹」，为空表示全部。其持久化值由 [initState] 注入，
 * 确保重启 APP 不丢失选中的文件夹。
 */
sealed class LocalState : SourceState {

    private val sortState = mutableStateOf<MediaSort>(MediaSort.DateDesc)
    private val scopeIdState = mutableStateOf("")
    private val loadingState = mutableStateOf(false)
    private val errorState = mutableStateOf<Throwable?>(null)

    override val sort: State<MediaSort> get() = sortState
    override val scopeId: State<String> get() = scopeIdState
    override val isLoading: State<Boolean> get() = loadingState
    override val error: State<Throwable?> get() = errorState

    override fun setSort(sort: MediaSort) {
        sortState.value = sort
    }

    override fun setScopeId(id: String) {
        scopeIdState.value = id
    }

    override fun setLoading(loading: Boolean) {
        loadingState.value = loading
    }

    override fun setError(error: Throwable?) {
        errorState.value = error
    }

    /**
     * 持久化的范围筛选初始值。每个单例映射到对应的 Preferences 字段，
     * 通过 [com.lollipop.common.tools.PreferencesBasic.TypedItem.get] 取出字符串值，
     * 由 [initState] 在 Application 统一初始化时注入。
     */
    protected abstract fun readPersistedScopeId(): String

    override fun initState() {
        scopeIdState.value = readPersistedScopeId()
    }

    object PublicVideo : LocalState() {
        override val visibility: MediaVisibility = MediaVisibility.Public
        override val mediaType: MediaType = MediaType.Video
        override fun readPersistedScopeId(): String {
            return Preferences.selectPublicVideoDir.get()
        }
    }

    object PrivateVideo : LocalState() {
        override val visibility: MediaVisibility = MediaVisibility.Private
        override val mediaType: MediaType = MediaType.Video
        override fun readPersistedScopeId(): String {
            return Preferences.selectPrivateVideoDir.get()
        }
    }

    object PublicImage : LocalState() {
        override val visibility: MediaVisibility = MediaVisibility.Public
        override val mediaType: MediaType = MediaType.Image
        override fun readPersistedScopeId(): String {
            return Preferences.selectPublicPhotoDir.get()
        }
    }

    object PrivateImage : LocalState() {
        override val visibility: MediaVisibility = MediaVisibility.Private
        override val mediaType: MediaType = MediaType.Image
        override fun readPersistedScopeId(): String {
            return Preferences.selectPrivatePhotoDir.get()
        }
    }

    companion object {
        /** 统一初始化所有 LocalState 单例，由 Application 在生命周期起点显式调用。 */
        fun initAll() {
            PublicVideo.initState()
            PrivateVideo.initState()
            PublicImage.initState()
            PrivateImage.initState()
        }
    }
}
