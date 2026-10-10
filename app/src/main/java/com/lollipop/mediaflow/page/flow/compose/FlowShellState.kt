package com.lollipop.mediaflow.page.flow.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.page.flow.ScreenRotate

/**
 * Flow 页 Compose 外壳的可观察状态。
 *
 * 与旧 View 外壳 [com.lollipop.mediaflow.ui.BasicFlowActivity] 的 `VisibleFilter` /
 * 直接写 View 属性的做法相比，这里**只描述"应该是什么"**：页面写入状态，
 * [FlowScaffold] 观察状态。所有显隐的合并规则（偏好 ∧ 非画中画 ∧ 朝向）都集中在
 * [FlowScaffold] 内计算，页面无需关心。
 *
 * 状态所有权：由 [com.lollipop.mediaflow.ui.BasicFlowComposeActivity] 持有并写入，
 * 页面（后续的 VideoFlowComposeActivity / PhotoFlowComposeActivity）通过其 protected
 * 方法间接修改，不直接触碰字段。
 */
@Stable
class FlowShellState {

    /** 装饰层（返回键 / 菜单栏 / 标题 / 标签）是否显示。 */
    var isDecorationVisible by mutableStateOf(true)

    /** 侧栏是否展开。 */
    var isSidePanelVisible by mutableStateOf(false)

    /**
     * 侧栏过渡进度（0 = 收起态，1 = 展开态），由 [FlowScaffold] 每帧写入。
     *
     * ⚠️ 使用约定：**只允许在绘制阶段（例如 `graphicsLayer` 的 lambda）读取**。
     * 它在动画期间每帧变化，若在组合中读取会让读取者每帧重组并重排——
     * 唯一需要这种行为的例外是装饰层（节点极少，且它本就该每帧跟随让位宽度）。
     */
    var panelProgress by mutableStateOf(0F)

    /**
     * 侧栏开关过渡期间的「内容抑制」标志（阶段信号）。
     *
     * **内容区的淡出 / 淡入由外壳负责**（[FlowScaffold] 的内容区容器读取本标志），
     * 因此页面不需要自己处理画面透明度。页面只应据此做两件它才知道的事：
     * 1. **暂停播放**，过渡结束后仅在「过渡前本来在播」时恢复；
     * 2. **刷新背景层素材**（如视频页把当前项的模糊图交给 `FlowBackground`）。
     *
     * 目的：把「内容区宽度变化 → 视频 Surface 尺寸变化」这次**一次性但昂贵**的重排，
     * 安排在画面不可见的静止时段，从而既拿到动画观感、又避免用户看到尺寸抖动或
     * 错误比例帧（同类成因见实施台账 M11-17）。
     */
    var isContentSuppressed by mutableStateOf(false)

    /** 是否处于用户选择的全屏；横屏下即使此值为 false 也会隐藏系统栏（对齐旧外壳）。 */
    var isFullscreen by mutableStateOf(false)

    /** 是否处于画中画模式：为 true 时装饰元素统一隐藏。 */
    var isPipMode by mutableStateOf(false)

    /** 是否竖屏：全屏按钮仅在竖屏出现（对齐旧外壳的 FullscreenBtnVisibleFilter）。 */
    var isPortrait by mutableStateOf(true)

    /** 当前旋转模式（图标与菜单选中态由它决定）。 */
    var screenRotate by mutableStateOf(ScreenRotate.ROTATE_LOCK)

    /** 标题（空串表示无标题，此时标题行不占位）。 */
    var title by mutableStateOf("")

    /** 标签列表（维度 / 时长 / 格式 / 体积），顺序即展示顺序。 */
    var tags by mutableStateOf(emptyList<FlowTag>())

    // ---- 偏好快照（onResume 时由 Activity 从 Preferences 读入） ----

    var showBackButton by mutableStateOf(true)
    var showSidePanelButton by mutableStateOf(true)
    var showFullscreenButton by mutableStateOf(true)
    var showRotateButton by mutableStateOf(true)
    var showTitle by mutableStateOf(true)
    var showTags by mutableStateOf(true)

    /** 标题是否跑马灯（false 时最多两行、尾部省略）。 */
    var isMarqueeTitle by mutableStateOf(true)

    /** 是否启用侧栏显隐手势（顶部横向滑动区域）。 */
    var sidePanelGestureEnabled by mutableStateOf(false)

    /**
     * 更新标题与标签。
     *
     * 参数顺序与旧外壳 [com.lollipop.mediaflow.ui.BasicFlowActivity.updateTitle] 对齐，
     * 便于页面迁移时直接替换调用。空标签自动剔除，全部为空时标签行不占位。
     */
    fun updateTitle(
        title: CharSequence,
        dimensions: CharSequence,
        size: CharSequence,
        format: CharSequence,
        duration: CharSequence
    ) {
        this.title = title.toString()
        val newTags = ArrayList<FlowTag>(TagSlotCount)
        if (dimensions.isNotEmpty()) {
            newTags.add(FlowTag(dimensions.toString()))
        }
        if (duration.isNotEmpty()) {
            newTags.add(FlowTag(duration.toString()))
        }
        if (format.isNotEmpty()) {
            newTags.add(FlowTag(format.toString()))
        }
        if (size.isNotEmpty()) {
            newTags.add(FlowTag(size.toString()))
        }
        tags = newTags
    }

    companion object {

        /**
         * 侧栏槽位宽度。
         *
         * 由外壳（让位宽度）与页面（过渡底图的视差平移量）**共用同一个值**，
         * 这样底图平移后其可见范围恰好等于动画中的内容区范围，两者严丝合缝。
         */
        val SidePanelWidth = 42.dp

        /**
         * 侧栏过渡：画面淡出 / 淡入时长（毫秒）。
         *
         * 取值偏长（而非"越快越好"）：短促的淡出淡入会被感知成"闪一下"，
         * 拖长到 300ms 才有"看清隐藏、看清显示"的观感（对齐 iOS 的系统动画节奏）。
         */
        const val PanelContentFadeMs = 300

        /**
         * 侧栏过渡：内容让位 + 侧栏平移的连续动画时长（毫秒）。
         *
         * 配合 `FastOutSlowInEasing`（cubic-bezier(0.4, 0, 0.2, 1)）产生明显速度变化，
         * 让人"看清移动过程"，而不是一闪而过。
         */
        const val PanelShiftMs = 350

        /** 标签槽位上限：维度 / 时长 / 格式 / 体积。 */
        private const val TagSlotCount = 4
    }
}

/**
 * 单个标签。
 *
 * 呈现方式对标 [com.lollipop.common.ui.view.TagTextView]：半透明白色圆角底块 + 镂空文字
 * （文字处透出下层画面），因此这里只承载文案，不区分"尺寸 / 时长"等语义。
 */
@Stable
data class FlowTag(val text: String)
