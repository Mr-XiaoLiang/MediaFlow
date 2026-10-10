package com.lollipop.mediaflow.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.lollipop.common.ui.page.PageOrientation
import com.lollipop.mediaflow.page.flow.ScreenRotate
import com.lollipop.mediaflow.page.flow.compose.FlowController
import com.lollipop.mediaflow.page.flow.compose.FlowScaffold
import com.lollipop.mediaflow.page.flow.compose.FlowShellState
import com.lollipop.mediaflow.tools.Preferences

/**
 * Flow 页的纯 Compose 外壳（与旧 View 外壳 [BasicFlowActivity] **平行**，迁移完成后删除旧的）。
 *
 * 继承树位置：
 * ```
 * BasicComposeActivity
 *  └─ BasicFlowComposeActivity   (本类：Flow 外壳：装饰层 / 菜单栏 / 侧栏容器 / insets / 旋转 / 全屏 / PiP)
 *      ├─ VideoFlowComposeActivity
 *      └─ PhotoFlowComposeActivity
 * ```
 *
 * 与旧外壳的差异：
 * - 抽屉宫格切换（`drawerLayout` / `menuBtn` / `MediaFlowStoreView`）**已下线**；
 * - 菜单栏 / 返回键的模糊底**已下线**，改为纯色底（沿用 `button_background`）；
 * - 装饰显隐、标题标签、侧栏显隐全部改为写入 [FlowShellState]，由 [FlowScaffold] 观察。
 *
 * 边界说明：insets 由 [BasicComposeActivity] 采集并桥接为 Compose 状态（单一来源）；
 * 画中画动作（上一首 / 下一首 / 播放暂停）与热键属于页面级能力，
 * 由具体页面（如视频页）注册，外壳只负责 PiP 状态与装饰层联动。
 */
abstract class BasicFlowComposeActivity : BasicComposeActivity() {

    /** 外壳状态：页面只通过本类的 protected 方法写入，Scaffold 只读。 */
    protected val shell = FlowShellState()

    override val useScaffold: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 恢复上次的旋转模式与全屏状态（对齐旧外壳 onCreate 的恢复逻辑）
        updateScreenRotate(
            ScreenRotate.findByTag(Preferences.lastRotateMode.get()) ?: ScreenRotate.ROTATE_LOCK
        )
        shell.isFullscreen = Preferences.isFullScreenEnable.get()
        updateFullscreen()
    }

    override fun onResume() {
        super.onResume()
        refreshShellPreferences()
    }

    /**
     * 组装外壳的 4 个容器。
     *
     * 容器本身（背景 / 内容区 / 侧栏 / 控制器）的形态变化由 [FlowScaffold] 负责，
     * 本类只负责「往容器里放什么」：
     * - 背景 → [FlowBackground]（默认空 = 窗口背景色）；
     * - 内容区 → [ContentPanel]；
     * - 侧栏 → [SidePanel]；
     * - 控制器 → [FlowController]（Flow 页默认的返回键 / 标题 / 菜单栏）。
     */
    @Composable
    override fun Content(innerPadding: PaddingValues) {
        FlowScaffold(
            state = shell,
            content = { ContentPanel() },
            onSidePanelChange = ::changeSidePanel,
            background = { FlowBackground() },
            sidePanel = { SidePanel() },
            controller = {
                FlowController(
                    state = shell,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onToggleFullscreen = {
                        shell.isFullscreen = !shell.isFullscreen
                        // 点击即记录（对齐旧外壳）
                        Preferences.isFullScreenEnable.set(shell.isFullscreen)
                        updateFullscreen()
                    },
                    onSelectRotate = ::updateScreenRotate,
                    onSidePanelChange = ::changeSidePanel,
                )
            },
        )
    }

    /**
     * 背景层（[FlowScaffold] 的第 1 个容器）。
     *
     * 默认不填充，露出窗口背景色（图片页即此语义）；视频页覆写为「黑底 + 视频模糊图」。
     * 平移由外壳统一负责，实现方只需把内容铺满容器。
     */
    @Composable
    protected open fun FlowBackground() {
    }

    /** 侧栏层内容（列表）。 */
    @Composable
    protected open fun SidePanel() {
    }

    /** 内容区（页面业务主体）。 */
    @Composable
    protected abstract fun ContentPanel()

    /** 把偏好读入外壳状态（显示开关、跑马灯、侧栏手势）。 */
    private fun refreshShellPreferences() {
        shell.showBackButton = Preferences.isShowBackBtn.get()
        shell.showSidePanelButton = Preferences.isShowSidePanelBtn.get()
        shell.showFullscreenButton = Preferences.isShowFullscreenBtn.get()
        shell.showRotateButton = Preferences.isShowRotateBtn.get()
        shell.showTitle = Preferences.isShowTitle.get()
        shell.showTags = Preferences.isShowTag.get()
        shell.isMarqueeTitle = Preferences.isMarqueeTitle.get()
        shell.sidePanelGestureEnabled = Preferences.isSidePanelGestureEnable.get()
    }

    /** 装饰层（返回键 / 菜单栏 / 标题 / 标签）显隐。 */
    protected fun changeDecoration(isVisible: Boolean) {
        shell.isDecorationVisible = isVisible
    }

    /** 侧栏显隐（菜单栏按钮与手势的统一入口）。 */
    protected fun changeSidePanel(isShow: Boolean) {
        shell.isSidePanelVisible = isShow
        onSidePanelUpdate(isShow)
    }

    /** 侧栏显隐变化回调（页面可据此更新列表内边距等）。 */
    protected open fun onSidePanelUpdate(isShown: Boolean) {
    }

    /**
     * 更新标题与标签。
     *
     * 签名与旧外壳 [BasicFlowActivity.updateTitle] 一致，页面迁移时可直接替换调用。
     */
    protected fun updateTitle(
        titleValue: CharSequence,
        dimensions: CharSequence,
        size: CharSequence,
        format: CharSequence,
        duration: CharSequence
    ) {
        shell.updateTitle(
            title = titleValue,
            dimensions = dimensions,
            size = size,
            format = format,
            duration = duration
        )
    }

    /**
     * 应用全屏状态。
     *
     * 与旧外壳一致：横屏时即使未选择全屏也隐藏系统栏。
     */
    protected fun updateFullscreen() {
        if (shell.isFullscreen || !shell.isPortrait) {
            hideSystemUI()
        } else {
            showSystemUI()
        }
    }

    /** 切换旋转模式：持久化 + 更新状态 + 请求系统朝向。 */
    protected fun updateScreenRotate(mode: ScreenRotate) {
        Preferences.lastRotateMode.set(mode.tag)
        shell.screenRotate = mode
        setRequestedOrientation(mode.tag)
    }

    override fun onOrientationChanged(orientation: PageOrientation) {
        shell.isPortrait = orientation == PageOrientation.PORTRAIT
        updateFullscreen()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        shell.isPipMode = isInPictureInPictureMode
    }
}
