package com.lollipop.mediaflow.data.local

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.lollipop.mediaflow.data.SourceState
import com.lollipop.mediaflow.data.common.MediaSort

/**
 * Local 专属的业务状态实现，实现了 [SourceState] 统一契约。
 *
 * 每个单例都自带它对应的 (visibility, mediaType) 与展示列表 [source]，
 * 因此一次加载请求只需把「某个模式实例」（如 [PublicVideo]）作为 [SourceState] 传入，
 * 控制层即可反解出要刷新的范围与要填充的列表，无需再查表。
 *
 * 注：范围筛选 [scopeId] 对应 Gallery 的 rootDirectoryId（choose 范围），
 * Local 解释为「只展示某文件夹」，为空表示全部。
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

    object PublicVideo : LocalState() {
        override val visibility = MediaVisibility.Public
        override val mediaType = MediaType.Video
    }

    object PrivateVideo : LocalState() {
        override val visibility = MediaVisibility.Private
        override val mediaType = MediaType.Video
    }

    object PublicImage : LocalState() {
        override val visibility = MediaVisibility.Public
        override val mediaType = MediaType.Image
    }

    object PrivateImage : LocalState() {
        override val visibility = MediaVisibility.Private
        override val mediaType = MediaType.Image
    }
}
