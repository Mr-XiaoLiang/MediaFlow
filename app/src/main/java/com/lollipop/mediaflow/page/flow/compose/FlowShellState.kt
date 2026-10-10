package com.lollipop.mediaflow.page.flow.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    private companion object {
        /** 标签槽位上限：维度 / 时长 / 格式 / 体积。 */
        const val TagSlotCount = 4
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
