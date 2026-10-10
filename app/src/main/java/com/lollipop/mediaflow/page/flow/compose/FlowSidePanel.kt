package com.lollipop.mediaflow.page.flow.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.ui.image.MediaImage
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * 侧栏（快速定位列表）：单列正方形缩略图 + 选中高亮，点击回跳。
 *
 * 与旧 [`com.lollipop.mediaflow.page.flow.FlowSidePanelDelegate`] 的对照：
 * - `RecyclerView + GalleryItemAdapter + ConcatAdapter(顶部/底部 Space)` → [LazyColumn]
 *   （**上下 insets 由 [`FlowScaffold`] 的侧栏容器统一留白**，无需再像旧实现那样插 Space 适配器）；
 * - `CoverLoader.load(imageView, mediaInfo)` → [`MediaImage`]（缩略图显式给 [ThumbnailSize]，控内存）；
 * - 选中态：旧的 `flagView` 角标 → 选中项描边（主色 `buttonSlider`）；
 * - `SelectionTracker + smoothScrollToPosition` → [rememberLazyListState] + [LaunchedEffect] 的
 *   `animateScrollToItem`：选中项变化即滚动到可见位置。
 *
 * 宽度由外层（`FlowScaffold` 的 42dp 槽位）决定，本组件只负责列表内容。
 */
@Composable
fun FlowSidePanel(
    items: List<MediaInfo.File>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onItemClick: (Int) -> Unit = {}
) {
    val listState = rememberLazyListState()
    // 选中项变化即定位（对齐旧实现的 SelectionTracker.onSelected → smoothScrollToPosition）
    LaunchedEffect(selectedIndex, items.size) {
        if (selectedIndex in items.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = items,
            key = { _, media -> media.uriString }
        ) { index, media ->
            FlowSidePanelItem(
                media = media,
                isSelected = index == selectedIndex,
                onClick = { onItemClick(index) }
            )
        }
    }
}

/** 单个缩略图项：正方形裁切 + 选中描边。 */
@Composable
private fun FlowSidePanelItem(
    media: MediaInfo.File,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeColor = currentThemeColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1F)
            .border(
                width = if (isSelected) SelectedBorderWidth else 0.dp,
                color = if (isSelected) themeColor.buttonSlider else Color.Transparent
            )
            .clickable(onClick = onClick)
    ) {
        MediaImage(
            data = media.uri,
            contentScale = ContentScale.Crop,
            targetSize = ThumbnailSize,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 缩略图目标解码尺寸（侧栏宽度 42dp，按 2x 密度估算）。 */
private val ThumbnailSize = IntSize(96, 96)

/** 选中描边宽度。 */
private val SelectedBorderWidth = 2.dp
