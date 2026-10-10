package com.lollipop.mediaflow.page.flow.compose.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lollipop.mediaflow.ui.home.plainTextStyle
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/** 单选项。 */
@Immutable
data class FlowChoiceItem(
    val label: String,
    val description: String = "",
    val selected: Boolean = false
)

/**
 * 通用单选弹窗（Compose 版）。
 *
 * 用于倍速选择与字幕轨道选择：样式对齐既有对话框（`button_background` 底、16dp 圆角、
 * 选中项高亮为主色 `buttonSlider`），列表超长时可滚动。
 *
 * 之所以做成通用件而不是各写一个：两处的交互（单选 + 点击即生效 + 点外部关闭）完全一致，
 * 差异只在数据来源。
 */
@Composable
fun FlowChoiceDialog(
    items: List<FlowChoiceItem>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val themeColor = currentThemeColor()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = themeColor.buttonBackground,
            modifier = Modifier
                .fillMaxWidth(DialogWidthFraction)
                .heightIn(max = DialogMaxHeight)
        ) {
            LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                itemsIndexed(items) { index, item ->
                    FlowChoiceRow(
                        item = item,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowChoiceRow(
    item: FlowChoiceItem,
    onClick: () -> Unit
) {
    val themeColor = currentThemeColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1F)) {
            Text(
                text = item.label,
                style = plainTextStyle(14.sp),
                color = if (item.selected) themeColor.buttonSlider else themeColor.buttonText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    style = plainTextStyle(11.sp),
                    color = themeColor.buttonText.copy(alpha = 0.7F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item.selected) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(color = themeColor.buttonSlider, shape = RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = SelectedFlag,
                    style = plainTextStyle(10.sp),
                    color = Color.White
                )
            }
        }
    }
}

/** 弹窗宽度占屏比例（`usePlatformDefaultWidth = false` 时自行约束）。 */
private const val DialogWidthFraction = 0.72F

/** 弹窗最大高度（超出滚动）。 */
private val DialogMaxHeight = 420.dp

/** 选中标记文案。 */
private const val SelectedFlag = "✓"
