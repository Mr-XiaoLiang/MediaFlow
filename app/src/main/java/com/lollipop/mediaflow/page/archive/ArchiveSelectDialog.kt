package com.lollipop.mediaflow.page.archive

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lollipop.mediaflow.data.local.ArchiveBasket
import com.lollipop.mediaflow.data.local.ArchiveManager
import com.lollipop.mediaflow.ui.archiveIcon
import com.lollipop.mediaflow.ui.home.plainTextStyle
import com.lollipop.mediaflow.ui.theme.MediaFlowTheme
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * 归档点选择弹窗（纯 Compose）。
 *
 * 与旧实现的对照：
 * - `BottomSheetDialog + RecyclerView + ConcatAdapter(ItemAdapter, BottomSpacerAdapter)`
 *   → [BottomSheetDialog] + [ComposeView] + [LazyColumn]（列表末尾保留原有的 60dp 底部留白）；
 * - `item_dialog_archive_select.xml` 的「图标 + 名称 + 地址」两行文本 → [ArchiveBasketItem]；
 * - 图标来源由 `ArchiveQuick.iconRes`（drawable）→ UI 层统一的 [archiveIcon]（Material 图标）。
 *
 * 外壳仍是 `Dialog`（而非 `DialogFragment`）：调用方 [`com.lollipop.mediaflow.tools.ArchiveHelper`]
 * 只有 `Context` 且带一次性回调闭包，保持原契约可以避免把回调跨重建保存的复杂度引入进来。
 */
class ArchiveSelectDialog(
    context: Context,
    private val onSelect: (ArchiveBasket) -> Unit
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(context).apply {
                setContent {
                    MediaFlowTheme {
                        ArchiveBasketList(
                            baskets = ArchiveManager.archiveBasketList,
                            onSelect = { basket ->
                                onSelect(basket)
                                dismiss()
                            }
                        )
                    }
                }
            }
        )
    }
}

/** 归档点列表：每行 = 图标 + 名称 + 地址，点击整行即选中。 */
@Composable
private fun ArchiveBasketList(
    baskets: List<ArchiveBasket>,
    onSelect: (ArchiveBasket) -> Unit
) {
    val textColor = currentThemeColor().buttonText
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items = baskets, key = { it.key }) { basket ->
            ArchiveBasketItem(
                basket = basket,
                textColor = textColor,
                onClick = { onSelect(basket) }
            )
        }
        // 底部留白：对齐旧实现追加的 `BottomSpacerAdapter`（60dp）
        item(key = BottomSpacerKey) {
            Spacer(modifier = Modifier.height(BottomSpacerHeight))
        }
    }
}

/** 单个归档点：图标 36dp（内缩 6dp）+ 名称 16sp + 地址 12sp。 */
@Composable
private fun ArchiveBasketItem(
    basket: ArchiveBasket,
    textColor: Color,
    onClick: () -> Unit
) {
    val basketType = remember(basket) { ArchiveManager.getBasketType(basket) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = basketType.archiveIcon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier
                .size(36.dp)
                .padding(6.dp)
        )
        Column(
            modifier = Modifier
                .weight(1F)
                .padding(start = 6.dp)
        ) {
            Text(
                text = basket.name,
                color = textColor,
                style = plainTextStyle(16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = basket.uriPath,
                color = textColor,
                style = plainTextStyle(12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 底部留白高度（对齐旧 `BottomSpacerAdapter` 的默认值）。 */
private val BottomSpacerHeight = 60.dp

/** 底部留白条目的列表 key。 */
private const val BottomSpacerKey = "archive_basket_bottom_spacer"
