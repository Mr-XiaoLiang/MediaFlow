package com.lollipop.mediaflow.ui.home

import android.view.Gravity
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.common.MediaSort
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.home.menu.ComposePopupMenuAnchor
import com.lollipop.mediaflow.ui.home.menu.HomeMenuKeys
import com.lollipop.mediaflow.ui.home.menu.rememberComposePopupMenu
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 首页各 Tab 的统一结构（远程来源 + 本地资源混合展示门户）。
 *
 * 1. 顶部 Slogan 胶囊（点击展开 Compose 操作气泡菜单、长按切换隐私模式）；
 * 2. 远程资源行（有数据才展示，每来源一行）；
 * 3. 本地资源标题行（Local 标记 + 文件夹选择 / 排序菜单胶囊）；
 * 4. 本地卡片瀑布流（固定宽度、自适应列数、上滑自动分页）。
 */
interface HomePageActions {
    fun onMediaClick(page: HomePage, index: Int)
    fun onSloganLongClick(page: HomePage)
    fun onFolderSelect(page: HomePage)
    fun onMenuAction(page: HomePage, tag: String)
    fun onAddSource(page: HomePage)
}

/**
 * 跨页面的滚动定位请求：播放页返回后，需要把对应 Tab 的列表定位到指定位置。
 * Compose 列表状态在页面内部，这里用一个共享的轻量总线把请求投递进去。
 */
object HomeScrollBus {

    private val targetState = mutableStateOf<Pair<HomePage, Int>?>(null)

    val target: State<Pair<HomePage, Int>?>
        get() = targetState

    fun request(page: HomePage, position: Int) {
        targetState.value = page to position
    }

    fun clear() {
        targetState.value = null
    }
}

/** 瀑布流中位于媒体卡片之前的固定头部条目数量（Slogan + Local 标题行）。 */
private const val FIXED_HEADER_ITEM_COUNT = 2

/** 卡片目标宽度（dp），与原 View 版 MediaStaggered.updateSpanCountVertical 的取值一致。 */
private const val MEDIA_CARD_WIDTH_DP = 150F
private const val MIN_COLUMN_COUNT = 1
private const val MAX_COLUMN_COUNT = 5

/**
 * 「滚回顶部」收尾动画的固定距离（dp）与时长（ms）。
 * 距离取 40dp：小于首个条目（Slogan 胶囊 42dp）的高度，确保瞬移始终朝顶部方向、不会反向跳动。
 */
private const val SCROLL_TO_TOP_FINAL_DISTANCE_DP = 40F
private const val SCROLL_TO_TOP_FINAL_DURATION_MS = 320

/**
 * 「用户已经看到首页」时，播放页返回定位前的等待时长（ms）。
 *
 * 用户尚在返回过渡中（还没看清列表）时直接瞬时归位、不做动画；只有确实已经盯着首页
 * （例如数据比首页晚到）才需要这段停顿让用户看清，然后再平滑移动过去。
 */
private const val POSITION_AFTER_VISIBLE_DELAY_MS = 300L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeMediaPage(
    page: HomePage,
    // 由 HomeScreen 下发的「滚回顶部」事件流：仅当 target 等于本页时才响应
    scrollToTopEvents: SharedFlow<HomePage>,
    contentPadding: PaddingValues,
    actions: HomePageActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val state = page.localState
    val source = page.source
    val mediaList = source.local
    val remoteList = source.webDAV
    // 列表只管「有数据 / 没数据」；是否提示用户，交给这个派生值（加载过但没有数据时为真）。
    val needAddSource by source.suggestAddSourceOrRefresh

    // 刷新状态：只喂下拉刷新指示器。
    val isLoading by state.isLoading

    val reloadTickState = remember { mutableIntStateOf(0) }
    val refreshRequested = remember { mutableStateOf(false) }
    val reloadTick = reloadTickState.intValue
    val showLabel = remember(reloadTick) {
        Preferences.isDisplayLabelInList.get()
    }

    // 列数与原 View 版一致：按窗口宽度除以 150dp 四舍五入，限制在 1..5 列
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val columnCount = remember(screenWidthDp) {
        ((screenWidthDp / MEDIA_CARD_WIDTH_DP) + 0.5F).toInt()
            .coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
    }

    val isDebugBuild = remember { context.packageName.endsWith(".debug") }
    val optionMenuState = rememberComposePopupMenu { builder ->
        builder
            .addMenu(HomeMenuKeys.SOURCE_MANAGER, R.string.source_manager, 0)
            .addMenu(HomeMenuKeys.PREFERENCES, R.string.preferences, 0)
            .addMenu(HomeMenuKeys.ARCHIVE_RENAME, R.string.archive_rename, 0)
            .addMenu(HomeMenuKeys.VIDEO_DUPLICATE, R.string.label_video_duplicate, 0)
            .addMenu(HomeMenuKeys.DEBUG_MODE, R.string.debug_mode, 0)
            .gravity(Gravity.END)
            .offsetDp(0, 8)
            .filter { item ->
                when (item.tag) {
                    HomeMenuKeys.DEBUG_MODE -> isDebugBuild
                    HomeMenuKeys.VIDEO_DUPLICATE -> page.mediaType == MediaType.Video
                    else -> true
                }
            }
            .onClick { item ->
                actions.onMenuAction(page, item.tag)
            }
    }
    val sortMenuState = rememberComposePopupMenu { builder ->
        builder
            .addMenu(MediaSort.DateDesc.key, R.string.sort_date_desc, R.drawable.clock_arrow_down_24)
            .addMenu(MediaSort.DateAsc.key, R.string.sort_date_asc, R.drawable.clock_arrow_up_24)
            .addMenu(MediaSort.NameDesc.key, R.string.sort_text_desc, R.drawable.text_arrow_down_24)
            .addMenu(MediaSort.NameAsc.key, R.string.sort_text_asc, R.drawable.text_arrow_up_24)
            .addMenu(MediaSort.Random.key, R.string.sort_random, R.drawable.shuffle_24)
            .gravity(Gravity.END)
            .offsetDp(0, 8)
            .onClick { item ->
                page.localState.setSort(
                    MediaSort.findByKey(item.tag) ?: MediaSort.DateDesc
                )
                scope.launch {
                    SourceLoader.Local.fill(context, page.localState)
                }
            }
    }

    val gridState = rememberLazyStaggeredGridState()
    val refreshState = rememberPullToRefreshState()

    // 媒体条目之前的头部条目数（Slogan / 远程行 / Local 标题行）：
    // 返回定位与右侧快速定位拖拽条都要用它把「列表索引」换算成「第 N 项」。
    val headerCount = FIXED_HEADER_ITEM_COUNT + if (remoteList.isEmpty()) 0 else 1

    // 每次回到前台：刷新偏好（Slogan / 标签开关）并从缓存投影一次数据
    val lifecycleOwner = remember(view) { view.findViewTreeLifecycleOwner() }
    LaunchedEffect(lifecycleOwner, state) {
        val owner = lifecycleOwner ?: return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            reloadTickState.intValue++
            SourceLoader.Local.fill(context, state)
            if (source.local.isEmpty() && !refreshRequested.value) {
                refreshRequested.value = true
                SourceLoader.Local.refresh(context, state)
            }
        }
    }

    // 播放页返回后定位到指定位置。
    // 按「用户是否已经看到首页」分成两条路径（见 [POSITION_AFTER_VISIBLE_DELAY_MS]）：
    // - 尚未看到（Activity 还在恢复中，请求由 onActivityResult 投递）：**瞬时归位、不做动画**，
    //   首帧就是正确位置，用户看不到任何移动过程；
    // - 已经看到（例如列表数据比首页晚到）：先停一下让用户看清当前画面，再平滑移动过去。
    val scrollTarget = HomeScrollBus.target.value
    LaunchedEffect(scrollTarget, mediaList.size) {
        val target = scrollTarget ?: return@LaunchedEffect
        if (target.first != page || mediaList.isEmpty()) {
            return@LaunchedEffect
        }
        val maxIndex = mediaList.size + headerCount - 1
        val targetIndex = (target.second + headerCount).coerceIn(0, maxIndex)
        val isVisible = lifecycleOwner?.lifecycle?.currentState
            ?.isAtLeast(Lifecycle.State.RESUMED) == true
        if (isVisible) {
            delay(POSITION_AFTER_VISIBLE_DELAY_MS)
            gridState.animateScrollToItem(targetIndex)
        } else {
            // 登记目标索引（无动画），在下一次测量即以该位置开始 —— 连「先渲染旧位置再跳」
            // 的那一帧都不会出现，观感就是「首页一出现就已经在正确位置」。
            gridState.requestScrollToItem(targetIndex)
        }
        HomeScrollBus.clear()
    }

    // 底部 Tab 胶囊手势：HomeScreen 定位到当前页后，经事件流下发「滚回顶部」。
    // 每个页面都监听同一条事件流，仅当 target 为本页时才滚动，天然只作用于当前 Tab。
    LaunchedEffect(page, scrollToTopEvents) {
        val finalPx = with(density) { SCROLL_TO_TOP_FINAL_DISTANCE_DP.dp.toPx() }.toInt()
        val spec = tween<Float>(SCROLL_TO_TOP_FINAL_DURATION_MS, easing = LinearOutSlowInEasing)
        scrollToTopEvents.collect { target ->
            if (target != page) {
                return@collect
            }
            // 效率优先：先「无动画」把位置瞬移到「距顶固定距离」处（目标区域在这一步一次性完成组合），
            // 随后只做一段固定距离的减速滚动收尾，落点恰好为顶部 —— 用户看到的就是最后一幕「滚到顶」。
            when {
                // 不在顶部：瞬移把剩余距离压到固定值（此动作朝顶），再减速滑到顶
                gridState.firstVisibleItemIndex > 0 ||
                    gridState.firstVisibleItemScrollOffset >= finalPx -> {
                    gridState.scrollToItem(0, finalPx)
                    gridState.animateScrollBy(-finalPx.toFloat(), spec)
                }
                // 已在顶部附近：直接对精确剩余距离做减速收尾
                else -> {
                    val offset = gridState.firstVisibleItemScrollOffset
                    if (offset > 0) {
                        gridState.animateScrollBy(-offset.toFloat(), spec)
                    }
                }
            }
        }
    }

    // 上滑接近底部时自动分页
    LaunchedEffect(gridState, state) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.distinctUntilChanged().collect { (last, total) ->
            if (total > 0 && last >= total - 2) {
                SourceLoader.Local.loadMore(context, state)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        state = refreshState,
        contentAlignment = Alignment.TopCenter,
        indicator = {
            // 使用 MD3 官方的形变加载指示器，替代旧版转圈样式
            PullToRefreshDefaults.LoadingIndicator(
                state = refreshState,
                isRefreshing = isLoading
            )
        },
        onRefresh = {
            scope.launch {
                SourceLoader.Local.refresh(context, state)
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columnCount),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalItemSpacing = 0.dp
        ) {
            item(span = StaggeredGridItemSpan.FullLine, key = "slogan") {
                ComposePopupMenuAnchor(
                    state = optionMenuState
                ) {
                    SloganBar(
                        reloadTick = reloadTick,
                        onClick = { optionMenuState.show() },
                        onLongClick = { actions.onSloganLongClick(page) }
                    )
                }
            }
            if (remoteList.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "remote") {
                    RemoteSourceRow(
                        sourceName = stringResource(R.string.source_webdav),
                        items = remoteList,
                        showLabel = showLabel,
                        onItemClick = { actions.onMediaClick(page, it) }
                    )
                }
            }
            item(span = StaggeredGridItemSpan.FullLine, key = "local_header") {
                LocalSectionHeader(
                    page = page,
                    sortMenuState = sortMenuState,
                    onFolderSelect = { actions.onFolderSelect(page) }
                )
            }
            // 没数据 + 建议干预 → 提示添加来源 / 刷新；否则（还没加载过）保持空白。
            if (mediaList.isEmpty() && remoteList.isEmpty() && needAddSource) {
                item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                    EmptyMediaView(
                        onAddSource = { actions.onAddSource(page) }
                    )
                }
            }
            items(
                count = mediaList.size,
                key = { index -> mediaList[index].mediaId }
            ) { index ->
                HomeMediaCard(
                    media = mediaList[index],
                    showLabel = showLabel,
                    onClick = { actions.onMediaClick(page, index) }
                )
            }
        }

        // 快速定位拖拽条：叠在列表之上（右侧），条目少时自动隐藏。
        // 总条目数用数据源算（媒体数 + 头部数），不依赖 layoutInfo —— 后者在数据刚变化时
        // 可能不完整，会让气泡序号退化成一屏内的范围。
        HomeFastScroller(
            state = gridState,
            totalItemCount = mediaList.size + headerCount,
            headerItemCount = headerCount,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun EmptyMediaView(onAddSource: () -> Unit) {
    val themeColor = currentThemeColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.hint_media_empty),
            color = themeColor.buttonText,
            style = plainTextStyle(18.sp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.btn_add_source),
            color = themeColor.buttonText,
            style = plainTextStyle(18.sp),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(CircleShape)
                .background(themeColor.buttonBackground)
                .clickable(onClick = onAddSource)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
