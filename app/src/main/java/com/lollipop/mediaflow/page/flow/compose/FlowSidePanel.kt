package com.lollipop.mediaflow.page.flow.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.ui.image.MediaImage
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * 侧栏（快速定位列表）：**长条胶囊缩略图** + 选中高亮，点击回跳。
 *
 * 与旧 [`com.lollipop.mediaflow.page.flow.FlowSidePanelDelegate`] 的对照：
 * - `RecyclerView + GalleryItemAdapter + ConcatAdapter(顶部/底部 Space)` → [LazyColumn]
 *   （**上下 insets 由 [`FlowScaffold`] 的侧栏容器统一留白**，无需再像旧实现那样插 Space 适配器）；
 * - `item_media_gallery.xml`（38dp × 68dp + 大圆角 + 上下各 2dp 外边距）→ [FlowSidePanelItem]
 *   的「宽度撑满槽位 + 固定 [ItemHeight] + [CapsuleShape] 胶囊裁切」，
 *   项间距由 [ItemVerticalSpacing] 提供（等价旧版两个 2dp 外边距累加）；
 * - `CoverLoader.load(imageView, mediaInfo)` → [`MediaImage`]（缩略图显式给 [ThumbnailSize]，控内存）；
 * - 选中态：旧的 `flagView` 角标 → 选中项胶囊描边（主色 `buttonSlider`）；
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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ItemVerticalSpacing)
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

/** 单个缩略图项：长条胶囊裁切（对齐旧 38×68dp 观感）+ 选中描边。 */
@Composable
private fun FlowSidePanelItem(
    media: MediaInfo.File,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeColor = currentThemeColor()
    Box(
        modifier = Modifier
            // 旧 `item_media_gallery.xml` 的 `layout_marginHorizontal = 2dp`：
            // 缩略图与槽位左右边缘留白，避免贴着视频画面显局促
            .padding(horizontal = ItemHorizontalMargin)
            .fillMaxWidth()
            .height(ItemHeight)
            .border(
                width = SelectedBorderWidth,
                color = if (isSelected) themeColor.buttonSlider else Color.Transparent,
                shape = CapsuleShape
            )
            .clip(CapsuleShape)
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

/** 胶囊形状：`percent = 50` 在窄长条上等价于左右两端全圆。 */
private val CapsuleShape = RoundedCornerShape(percent = 50)

/** 缩略图高度（旧 `item_media_gallery.xml` 的 68dp）。 */
private val ItemHeight = 68.dp

/** 相邻缩略图间距（旧实现上下各 2dp 外边距累加）。 */
private val ItemVerticalSpacing = 4.dp

/** 缩略图左右外边距（旧 `layout_marginHorizontal = 2dp`）。 */
private val ItemHorizontalMargin = 2.dp

/** 缩略图目标解码尺寸（约 38dp × 68dp，按 2x 密度估算）。 */
private val ThumbnailSize = IntSize(96, 172)

/** 选中描边宽度。 */
private val SelectedBorderWidth = 2.dp
