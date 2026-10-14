package com.lollipop.mediaflow.ui.home

import android.app.Activity
import android.view.Gravity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.effect.EdgeBand
import com.lollipop.mediaflow.ui.effect.EdgeBandFadeExtra
import com.lollipop.mediaflow.ui.effect.EdgeEffectBandFromContext
import com.lollipop.mediaflow.ui.effect.FloatingSurfaceElevation
import com.lollipop.mediaflow.ui.effect.effectSourceFromContext
import com.lollipop.mediaflow.ui.effect.effectSurfaceFromContext
import com.lollipop.mediaflow.ui.home.menu.ComposePopupMenuAnchor
import com.lollipop.mediaflow.ui.home.menu.HomeMenuKeys
import com.lollipop.mediaflow.ui.home.menu.rememberComposePopupMenu
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlinx.coroutines.flow.MutableSharedFlow

/** 悬浮 Slogan 胶囊距安全区顶部的间距。 */
private val SloganBarTopMargin = 8.dp

/**
 * 「回到顶部」悬浮按钮：直径与内部图标尺寸。
 *
 * 直径 42dp：与 SloganBar 胶囊高度、返回键胶囊按钮宽度保持同一套悬浮尺寸。
 */
private val BackToTopButtonSize = 42.dp
private val BackToTopIconSize = 24.dp

/** 「回到顶部」按钮距右侧 / 底部安全区的间距。 */
private val BackToTopMargin = 16.dp

/**
 * 首页根布局（纯 Compose）：
 * - [HorizontalPager] 承载各 Tab 页面，替代 ViewPager2；
 * - [rememberSaveableStateHolder] 保证页面被回收后仍能恢复状态（滚动位置等）；
 * - 顶部悬浮 Slogan 胶囊：既是菜单入口（长按切换隐私），也以「轮廓线高亮」表示当前 Tab；
 * - 安全区通过 [WindowInsets.safeDrawing] 处理，兼容不同系统版本与挖孔/手势条。
 */
@Composable
fun HomeScreen(
    actions: HomePageActions,
    modifier: Modifier = Modifier
) {
    val locked by PrivacyLock.lockState
    // 用 rememberUpdatedState 包一层，保证 pageCount / 页面取值的 lambda 始终读到最新页面集合
    val pagesState = rememberUpdatedState(
        if (locked) HomePage.publicPages else HomePage.entries
    )

    val pagerState = rememberPagerState(pageCount = { pagesState.value.size })
    val stateHolder = rememberSaveableStateHolder()
    // 「滚回顶部」事件流：右下角悬浮按钮 → HomeScreen 定位当前页 → 下发给对应页面消费
    val scrollToTopEvents = remember { MutableSharedFlow<HomePage>(extraBufferCapacity = 1) }
    // 各页「是否已在顶部」的实时状态（键为页面，值为 true 表示在顶部）：由各页自行上报。
    // 这里只取「当前页」的值驱动按钮显隐，因此横向切页时天然联动：
    // A 页不在顶部会显示，切到已在顶部的 B 页后随即隐藏。
    val atTopStates = remember { mutableStateMapOf<HomePage, Boolean>() }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current


    val context = LocalContext.current
    val density = LocalDensity.current
    val isDebugBuild = remember { context.packageName.endsWith(".debug") }

    // 悬浮胶囊实测高度（像素）：列表顶部据此让位，避免内容被悬浮栏遮挡
    var sloganBarHeightPx by remember(density) {
        mutableIntStateOf(with(density) { SloganBarMinHeight.roundToPx() })
    }

    // 「回到前台」时递增：让 SloganBar 重新读取自定义文案（从设置页返回后保持一致）
    var reloadTick by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val lifecycleOwner = remember(view) { view.findViewTreeLifecycleOwner() }
    DisposableEffect(lifecycleOwner) {
        val owner = lifecycleOwner
        if (owner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    reloadTick++
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }

    // 菜单条目过滤 / 动作都依赖「当前页」，这里在调用时按 Pager 位置实时取值
    val menuState = rememberComposePopupMenu { builder ->
        builder
            .addMenu(HomeMenuKeys.SOURCE_MANAGER, R.string.source_manager, 0)
            .addMenu(HomeMenuKeys.PREFERENCES, R.string.preferences, 0)
            .addMenu(HomeMenuKeys.ARCHIVE_RENAME, R.string.archive_rename, 0)
            .addMenu(HomeMenuKeys.VIDEO_DUPLICATE, R.string.label_video_duplicate, 0)
            .addMenu(HomeMenuKeys.DEBUG_MODE, R.string.debug_mode, 0)
            .gravity(Gravity.END)
            .offsetDp(0, 8)
            .filter { item ->
                val page = currentHomePage(pagesState, pagerState)
                when (item.tag) {
                    HomeMenuKeys.DEBUG_MODE -> isDebugBuild
                    HomeMenuKeys.VIDEO_DUPLICATE -> page?.mediaType == MediaType.Video
                    else -> true
                }
            }
            .onClick { item ->
                val page = currentHomePage(pagesState, pagerState) ?: return@onClick
                actions.onMenuAction(page, item.tag)
            }
    }

    // 隐私模式切换导致页面集合变化时，回到第一页
    LaunchedEffect(locked) {
        if (pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

    // 当前页「未在顶部」时才显示「回到顶部」按钮；状态未知（尚未上报）时按「在顶部」处理，避免误显示。
    val currentPage = currentHomePage(pagesState, pagerState)
    val showBackToTop = currentPage?.let { atTopStates[it] } == false

    UpdateStatusBarAppearance()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentThemeColor().windowBackground)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                // 内容 Pager 作为模糊数据来源（与页面级浮层共享同一 HazeState）
                .effectSourceFromContext(),
            key = { index -> pagesState.value.getOrNull(index)?.key ?: "index_$index" },
            beyondViewportPageCount = 1
        ) { index ->
            val page = pagesState.value.getOrNull(index) ?: return@HorizontalPager
            stateHolder.SaveableStateProvider(page.key) {
                HomeMediaPage(
                    page = page,
                    scrollToTopEvents = scrollToTopEvents,
                    onAtTopChanged = { changedPage, atTop -> atTopStates[changedPage] = atTop },
                    contentPadding = PaddingValues(
                        // 与原 View 版一致：列表左右各 4dp（卡片自身另有 4dp），
                        // 因此卡片间距/贴边 8dp，而 Slogan、标题行这类自带 16dp 的条目落在 20dp 处。
                        start = insets.calculateLeftPadding(layoutDirection) + 4.dp,
                        end = insets.calculateRightPadding(layoutDirection) + 4.dp,
                        // 顶部在安全区与间距之上，再让出悬浮 SloganBar 的实测高度，避免列表被遮挡
                        top = insets.calculateTopPadding() + SloganBarTopMargin +
                            with(density) { sloganBarHeightPx.toDp() },
                        bottom = insets.calculateBottomPadding() + 72.dp
                    ),
                    actions = actions
                )
            }
        }

        // 底部系统栏边缘带：覆盖导航栏 / 手势条区域，朝内容中心 alpha 渐隐
        // 系统栏边缘带：只覆盖系统栏区域；全屏时 inset 为 0 → 不添加
        if (insets.calculateBottomPadding() > 0.dp) {
            EdgeEffectBandFromContext(
                edge = EdgeBand.BOTTOM,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(insets.calculateBottomPadding() + EdgeBandFadeExtra)
            )
        }

        // 顶部系统栏边缘带：覆盖状态栏区域，朝屏幕中心 alpha 渐隐。
        // 首页虽有悬浮 Slogan 胶囊，但胶囊位于状态栏下方，内容仍会绘制到状态栏区域，故仍需保护（Q13）。
        if (insets.calculateTopPadding() > 0.dp) {
            EdgeEffectBandFromContext(
                edge = EdgeBand.TOP,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .height(insets.calculateTopPadding() + EdgeBandFadeExtra)
            )
        }

        // 唯一的悬浮 SloganBar：菜单锚点、轮廓线 Tab、手势均在此承载
        ComposePopupMenuAnchor(
            state = menuState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = insets.calculateTopPadding() + SloganBarTopMargin)
        ) {
            SloganBar(
                pagerState = pagerState,
                pageCount = pagesState.value.size,
                reloadTick = reloadTick,
                onClick = { menuState.show() },
                onLongClick = {
                    currentHomePage(pagesState, pagerState)?.let { actions.onSloganLongClick(it) }
                },
                onBarHeightChanged = { sloganBarHeightPx = it }
            )
        }

        // 右下角「回到顶部」悬浮按钮：仅当前页不在顶部时显示（缩放进出）。
        // 与 SloganBar 同款悬浮处理（阴影 + 模糊 + 圆形 + 主题色）。它是 Pager 的兄弟节点，
        // 因此构成合法的模糊 surface（Pager 为 source）。
        BackToTopButton(
            visible = showBackToTop,
            onClick = { currentPage?.let { scrollToTopEvents.tryEmit(it) } },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = BackToTopMargin,
                    bottom = insets.calculateBottomPadding() + BackToTopMargin
                )
        )
    }
}

/**
 * 右下角「回到顶部」悬浮按钮。
 *
 * 与 SloganBar 保持同一套悬浮观感：同一投影高度（[FloatingSurfaceElevation]，颜色随明暗主题切换）、
 * 1dp 描边、模糊特效（[effectSurfaceFromContext]；无特效时为纯色 + 投影 + 描边）、
 * 圆形与主题按钮配色。显隐采用缩放动画：放大出现、缩小消失。
 */
@Composable
private fun BackToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = currentThemeColor()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.5F),
        exit = fadeOut() + scaleOut(targetScale = 0.5F)
    ) {
        Box(
            modifier = Modifier
                .size(BackToTopButtonSize)
                // 投影 + 描边统一由 effectSurface 提供（投影颜色随明暗主题切换）
                .effectSurfaceFromContext(
                    shape = CircleShape,
                    fallbackColor = themeColor.buttonBackground,
                    shadowElevation = FloatingSurfaceElevation,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = themeColor.buttonText,
                modifier = Modifier.size(BackToTopIconSize)
            )
        }
    }
}

/**
 * 取 Pager 当前落位对应的页面；页面集合在隐私切换瞬间可能尚未同步，故做空值兜底。
 */
private fun currentHomePage(
    pagesState: State<List<HomePage>>,
    pagerState: PagerState
): HomePage? {
    val pages = pagesState.value
    return pages.getOrNull(pagerState.currentPage) ?: pages.firstOrNull()
}

@Composable
private fun UpdateStatusBarAppearance() {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !isDark
        }
    }
}
