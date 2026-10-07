package com.lollipop.mediaflow.ui.home

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMediaPage(
    page: HomePage,
    contentPadding: PaddingValues,
    actions: HomePageActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val state = page.localState
    val source = page.source
    val mediaList = source.local
    val remoteList = source.webDAV

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
            .addMenu(HomeMenuKeys.ARCHIVE, R.string.archive, 0)
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

    // 播放页返回后定位到指定位置
    val scrollTarget = HomeScrollBus.target.value
    LaunchedEffect(scrollTarget, mediaList.size) {
        val target = scrollTarget ?: return@LaunchedEffect
        if (target.first != page || mediaList.isEmpty()) {
            return@LaunchedEffect
        }
        val headerCount = FIXED_HEADER_ITEM_COUNT + if (remoteList.isEmpty()) 0 else 1
        val maxIndex = mediaList.size + headerCount - 1
        gridState.animateScrollToItem((target.second + headerCount).coerceIn(0, maxIndex))
        HomeScrollBus.clear()
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
            if (mediaList.isEmpty() && remoteList.isEmpty() && !isLoading) {
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
