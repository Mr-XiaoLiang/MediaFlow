package com.lollipop.mediaflow.ui.home

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 首页根布局（纯 Compose）：
 * - [HorizontalPager] 承载各 Tab 页面，替代 ViewPager2；
 * - [rememberSaveableStateHolder] 保证页面被回收后仍能恢复状态（滚动位置等）；
 * - 底部悬浮胶囊 Tab（纯色 + 阴影 + 滑块动画）；
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
    // 「滚回顶部」事件流：Tab 胶囊手势 → HomeScreen 定位当前页 → 下发给对应页面消费
    val scrollToTopEvents = remember { MutableSharedFlow<HomePage>(extraBufferCapacity = 1) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current

    // 隐私模式切换导致页面集合变化时，回到第一页
    LaunchedEffect(locked) {
        if (pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

    UpdateStatusBarAppearance()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentThemeColor().windowBackground)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> pagesState.value.getOrNull(index)?.key ?: "index_$index" },
            beyondViewportPageCount = 1
        ) { index ->
            val page = pagesState.value.getOrNull(index) ?: return@HorizontalPager
            stateHolder.SaveableStateProvider(page.key) {
                HomeMediaPage(
                    page = page,
                    scrollToTopEvents = scrollToTopEvents,
                    contentPadding = PaddingValues(
                        // 与原 View 版一致：列表左右各 4dp（卡片自身另有 4dp），
                        // 因此卡片间距/贴边 8dp，而 Slogan、标题行这类自带 16dp 的条目落在 20dp 处。
                        start = insets.calculateLeftPadding(layoutDirection) + 4.dp,
                        end = insets.calculateRightPadding(layoutDirection) + 4.dp,
                        top = insets.calculateTopPadding() + 8.dp,
                        bottom = insets.calculateBottomPadding() + 72.dp
                    ),
                    actions = actions
                )
            }
        }
        HomeTabBar(
            pages = pagesState.value,
            pagerState = pagerState,
            onScrollToTop = {
                // 手指在 fab 上松手：由 Pager 定位当前页，向它下发「滚回顶部」事件
                pagesState.value.getOrNull(pagerState.currentPage)?.let { page ->
                    scrollToTopEvents.tryEmit(page)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = insets.calculateBottomPadding() + 10.dp)
        )
    }
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
