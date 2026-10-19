package com.lollipop.mediaflow.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lollipop.mediaflow.ui.page.PageOrientation
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
        updateStatusBarAppearance()
    }

    override fun onResume() {
        super.onResume()
        refreshShellPreferences()
        updateStatusBarAppearance()
    }

    override fun onStop() {
        super.onStop()
        // 离开 Flow 页时恢复系统栏：避免「全屏隐藏系统栏」的副作用残留到后续页面
        // （表现为设置页等系统栏位置的模糊带消失）。配置变更（旋转）交由重建后的
        // onCreate → updateFullscreen 处理；画中画下不干预系统栏。
        if (!isChangingConfigurations && !shell.isPipMode) {
            showSystemUI()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 配置变化（如日夜切换）后系统可能重置系统栏外观，这里重新锁定
        updateStatusBarAppearance()
    }

    /**
     * 状态栏与导航栏内容外观**强制深色模式**：图标 / 文字恒为浅色（白色）。
     *
     * Flow 页内容以媒体画面为主，无论系统是深色还是浅色主题，白色系统栏图标都最清晰稳定，
     * 因此这里不再跟随主题，直接锁定为「深色模式」。
     */
    private fun updateStatusBarAppearance() {
        setAppearanceLightStatusBars(false)
        setAppearanceLightNavigationBars(false)
    }

    /**
     * 页面级系统栏模糊开关：全屏（含横屏强制全屏）时系统栏被隐藏，
     * 此时不渲染系统栏边缘带，避免在无系统栏的区域留下多余的模糊带。
     *
     * 该开关只作用于**本页**——其它页面（如设置页）沿用 Base 默认值 `true`，
     * 因此「全屏强制关模糊」不会外溢。
     */
    @Composable
    override fun isSystemBarBlurEnabled(): Boolean {
        return !shell.isFullscreen && shell.isPortrait
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
        // 特效上下文（ProvideVisualEffect）由 [BasicComposeActivity] 在页面根统一提供，
        // 以便页面级浮层（OverlayHost）与页面内容共享同一个 HazeState。
        // 脚手架与页面级浮层**平级**：浮层铺在脚手架之上，因此不受侧栏让位影响，
        // 触摸也不会漏到下层（详见 [Overlay]）。
        Box(modifier = Modifier.fillMaxSize()) {
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
            Overlay()
        }
    }

    /**
     * 页面级浮层：位于 [FlowScaffold] **之外**、铺满整屏。
     *
     * 用于整图预览、对话框等「必须覆盖侧栏与装饰层」的内容（对齐旧实现挂在
     * `android.R.id.content` 上的做法）：它在脚手架之上绘制并在其上层接收触摸，
     * 因此既不会随侧栏让位收窄，也不会把点击漏给侧栏。
     */
    @Composable
    protected open fun Overlay() {
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
        shell.showTuneButton = Preferences.isShowTuneBtn.get()
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
