package com.lollipop.mediaflow.data

import androidx.compose.runtime.State
import com.lollipop.mediaflow.data.common.MediaSort
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.data.local.MediaVisibility

/**
 * 加载 / 业务状态的统一契约。
 *
 * 每次加载请求都要求传入一个 [SourceState] 实例，不同来源（Local / WebDAV）
 * 各自提供自己的实现（例如 Local 的 [com.lollipop.mediaflow.data.local.LocalState]），
 * 把对应的某个模式实例（PublicVideo 等）放进来。控制层只依赖本接口，
 * 通过它读取 / 翻转状态，UI 也只观察它。接口保持通用，控制层不向下转型具体类型。
 */
interface SourceState {

    /** 访问权限（Public / Private），决定底层一次读取缓存哪个范围。 */
    val visibility: MediaVisibility

    /** 展示筛选（Image / Video），决定投影到哪个 MediaSource 列表。 */
    val mediaType: MediaType

    /** 排序业务状态。 */
    val sort: State<MediaSort>

    /** 范围筛选状态（文件夹 / 网盘目录等），由各来源自行解释。空表示「全部」。 */
    val scopeId: State<String>

    /** 加载中状态，刷新开始置 true、无论成败结束都置 false。 */
    val isLoading: State<Boolean>

    /** 最近一次刷新失败的原因（成功则为 null）。 */
    val error: State<Throwable?>

    fun setSort(sort: MediaSort)
    fun setScopeId(id: String)
    fun setLoading(loading: Boolean)
    fun setError(error: Throwable?)

    /**
     * 统一初始化：类的加载早于生命周期，那时拿不到持久化数据，
     * 且 State<> 不适合懒加载，所以由 Application 在生命周期起点显式调用，
     * 把持久化的初始值（如 scopeId）注入到状态里。
     */
    fun initState()
}
