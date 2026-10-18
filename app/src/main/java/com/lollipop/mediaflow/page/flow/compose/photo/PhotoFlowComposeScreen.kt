package com.lollipop.mediaflow.page.flow.compose.photo

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.uriString
import com.lollipop.mediaflow.ui.effect.effectSourceFromContext
import com.lollipop.mediaflow.ui.image.MediaImage
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 图片页（纯 Compose）：`LazyVerticalGrid` 单列等比布局。
 *
 * 单图预览不在本组件内渲染：它需要覆盖侧栏与装饰层（旧实现挂在 `android.R.id.content` 上），
 * 因此由页面在 overlay 插槽（[com.lollipop.mediaflow.ui.BasicFlowComposeActivity.Overlay]）承载；
 * 本组件只负责把「被点中的那一项」的**完整矩形**（根坐标系，不裁剪到屏幕内）上抛，
 * 供预览层做共享元素式进出场动画（详见 [FlowPhotoPreview]）。
 *
 * 与旧 `PhotoFlowActivity` 的对照：
 * - `RecyclerView + LinearLayoutManager + MediaGrid 边缘装饰` → [LazyVerticalGrid]（`GridCells.Fixed(1)`）；
 * - `RatioFrameLayout` 按 `MetadataLoader` 的宽高设比例 → [`aspectRatio`]（宽度优先，等价 `Mode.WidthFirst`）；
 * - `CoverLoader.load(ImageView, override)` → [`MediaImage`]（Glide，`override()` 控尺寸，沿用同样的 1440×3168 上限）；
 * - 滚动空闲时 `onSelected(firstCompletelyVisiblePosition)` → 这里观察 [rememberLazyGridState] 的首项索引上报
 *   （「滚动落位选位」，与旧语义一致：滚动停稳才算选中）；
 * - 全屏预览 → [FlowPhotoPreview]（保留 `SubsamplingScaleImageView`）。
 *
 * 数据由调用方传入（与视频页一致：观察 `MediaSource` 投影，不拷贝列表）。
 */
@Composable
fun PhotoFlowComposeScreen(
    items: List<LMedia>,
    initialIndex: Int = 0,
    isArchiveEnabled: Boolean = true,
    /** 侧栏点击后的跳转请求（`>= 0` 时生效，消费后回调 [onScrollRequestHandled]）。 */
    scrollToIndex: Int = NoScrollRequest,
    onScrollRequestHandled: () -> Unit = {},
    onIndexChanged: (Int) -> Unit = {},
    onArchiveClick: (LMedia) -> Unit = {},
    /**
     * 点击单张图片：由页面在 overlay 层打开预览。
     *
     * @param ratio          该 item 的显示比例；`null` 表示元数据未就绪（预览层据此退化为纯淡入）。
     * @param originProvider 读取该 item **当前**在根坐标系中的完整矩形（可能部分在屏幕外）。
     *   这里刻意传「读取函数」而不是矩形快照：转屏后 Activity 不重建，但 item 会按新方向重新
     *   布局，预览层每次组合都取到最新矩形，进出场动画才不会沿用旧方向的坐标。
     */
    onPreview: (uri: Uri, ratio: Float?, originProvider: () -> Rect) -> Unit = { _, _, _ -> }
) {
    if (items.isEmpty()) {
        return
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(items.indices)
    )

    // 滚动落位选位：滚动停止后上报当前首项（对齐旧 onScrollStateChanged IDLE 的时机）
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress to gridState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { (isScrolling, firstIndex) ->
                if (!isScrolling) {
                    onIndexChanged(firstIndex)
                }
            }
    }

    // 侧栏点击 → 跳转到对应项
    LaunchedEffect(scrollToIndex) {
        if (scrollToIndex in items.indices) {
            gridState.animateScrollToItem(scrollToIndex)
            onScrollRequestHandled()
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(1),
        // 图片内容作为模糊数据来源（source）；控制层（返回键 / 菜单栏）在其外层，不交叉
        modifier = Modifier
            .fillMaxSize()
            .effectSourceFromContext(),
        // 首尾留白：首项不压状态栏、末项不压导航栏（旧实现用首尾 Space 装饰达到同一目的）
        contentPadding = WindowInsets.systemBars.asPaddingValues()
    ) {
        itemsIndexed(
            items = items,
            key = { _, media -> media.uriString }
        ) { index, media ->
            FlowPhotoItem(
                // 项增删过渡（LazyGridItemScope.animateItem）：归档移除时不再硬切
                modifier = Modifier.animateItem(),
                media = media,
                isArchiveEnabled = isArchiveEnabled,
                onClick = { ratio, originProvider ->
                    onPreview(media.uri, ratio, originProvider)
                },
                onArchiveClick = { onArchiveClick(media) }
            )
        }
    }
}

/**
 * 单张图片：等比卡片 + 可选归档按钮。
 *
 * @param onClick 回传该项的显示比例与**完整矩形的读取函数**：矩形由
 *   `positionInRoot()` + 布局尺寸得到（**不能用 `boundsInRoot()`**，后者默认按 root 边界
 *   裁剪，卡片部分滚出屏幕时会把矩形裁到屏幕边缘，动画就只能缩到边缘再跳回原位）；
 *   传读取函数则保证转屏重新布局后，预览层拿到的是新矩形而不是点击瞬间的快照。
 */
@Composable
private fun FlowPhotoItem(
    modifier: Modifier = Modifier,
    media: LMedia,
    isArchiveEnabled: Boolean,
    onClick: (ratio: Float?, originProvider: () -> Rect) -> Unit,
    onArchiveClick: () -> Unit
) {
    val ratio = rememberPhotoRatio(media)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // 关闭点击波纹：波纹是在 item 原地绘制的按压反馈，而点击后会立刻开始「图片放大到全屏」
    // 的共享元素动画，两者叠加时观感就是按下瞬间闪一下，故这里只保留动画、不保留波纹。
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                // 取完整布局矩形：位置用 positionInRoot（不裁剪），尺寸用布局尺寸。
                // 若改用 boundsInRoot()，它会按 root 边界裁剪——卡片一部分在屏幕外时，
                // 矩形被裁到屏幕边缘，动画就只能移动到边缘，随后浮层移除再跳回列表位置。
                bounds = Rect(
                    offset = coordinates.positionInRoot(),
                    size = coordinates.size.toSize()
                )
            }
            .aspectRatio(ratio ?: DefaultPhotoRatio)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(ratio) { bounds } }
            )
    ) {
        MediaImage(
            data = media.uri,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        if (isArchiveEnabled) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(PhotoArchivePadding)
                    .size(PhotoArchiveIconSize)
                    .clickable(onClick = onArchiveClick)
            )
        }
    }
}

/**
 * 图片展示比例（宽 / 高）。
 *
 * 与旧 `PhotoItemHolder.bind` 同一口径：`needRotate` 时交换宽高。
 * 返回 `null` 表示元数据尚未就绪——列表用 [DefaultPhotoRatio] 兜底布局，
 * 预览层则据此跳过位移动画（避免起点与终点比例不一致造成跳变）。
 */
@Composable
private fun rememberPhotoRatio(media: LMedia): Float? {
    val context = LocalContext.current
    var ratio by remember(media) { mutableStateOf<Float?>(null) }
    LaunchedEffect(media) {
        // MetadataLoader 内部复用同一个加载任务，且在主线程回调
        media.loadMetadata(context) { metadata ->
            ratio = metadata?.let { value ->
                val width = if (value.needRotate) value.height else value.width
                val height = if (value.needRotate) value.width else value.height
                if (width > 0 && height > 0) {
                    width.toFloat() / height.toFloat()
                } else {
                    null
                }
            }
        }
    }
    return ratio
}

/** 元数据未知时的兜底比例（正方形）。 */
private const val DefaultPhotoRatio = 1F

/**
 * 归档按钮图标尺寸（32dp）：与视频页控件按钮的内容区（`FlowVideoControlLayer` 的
 * `ControlContentSize`）保持一致，避免两页同一功能按钮的观感不一致。
 */
private val PhotoArchiveIconSize = 32.dp

/** 归档按钮距卡片右下角的内边距。 */
private val PhotoArchivePadding = 8.dp

/** 无跳转请求。 */
private const val NoScrollRequest = -1
