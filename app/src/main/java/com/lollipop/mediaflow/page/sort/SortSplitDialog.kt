package com.lollipop.mediaflow.page.sort

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.ui.dialog.ComposeHalfDialog
import com.lollipop.mediaflow.ui.home.plainTextStyle
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * 分割关键词设置对话框（复用半屏 Dialog 外壳）。
 *
 * 布局：**上方为可滚动区**（已启用 + 未启用），**底部固定输入行**。
 * - 已启用：`InputChip` + 右侧小号 X，点击即移回未启用；顺序即匹配优先级；
 * - 未启用：`FilterChip`，点击即追加到已启用末尾；
 * - 输入行：输入新关键词并「添加」到未启用，固定在底部不随滚动移动。
 *
 * 改动直接写入全局的 [SortSplitKeywords]（可观察 + 持久化），
 * 页面侧的排序会自动重算，无需回调宿主。
 */
class SortSplitDialog : ComposeHalfDialog() {

    companion object {
        private const val TAG = "SortSplitDialog"

        /** 已启用标签上 X 的尺寸：刻意小于文字，避免喧宾夺主。 */
        private val CloseIconSize = 14.dp

        fun show(fragmentManager: FragmentManager) {
            SortSplitDialog().show(fragmentManager, TAG)
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun DialogContent() {
        var input by remember { mutableStateOf("") }
        val textColor = currentThemeColor().buttonText
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 可滚动区：已启用 + 未启用
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1F)
                    .verticalScroll(rememberScrollState())
            ) {
                SectionTitle(text = getString(R.string.sort_split_enabled), color = textColor)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SortSplitKeywords.enabled.forEach { keyword ->
                        InputChip(
                            selected = true,
                            onClick = { SortSplitKeywords.disable(keyword) },
                            label = { Text(labelOf(keyword)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = getString(R.string.sort_split_remove),
                                    modifier = Modifier
                                        .size(CloseIconSize)
                                        .clickable { SortSplitKeywords.disable(keyword) }
                                )
                            }
                        )
                    }
                }

                SectionTitle(
                    text = getString(R.string.sort_split_disabled),
                    color = textColor,
                    topPadding = 16.dp
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SortSplitKeywords.disabled.forEach { keyword ->
                        FilterChip(
                            selected = false,
                            onClick = { SortSplitKeywords.enable(keyword) },
                            label = { Text(labelOf(keyword)) }
                        )
                    }
                }
            }

            // 固定底部：输入框 + 添加
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1F),
                    singleLine = true,
                    label = { Text(getString(R.string.sort_split_input_hint)) }
                )
                Button(
                    modifier = Modifier.padding(start = 8.dp),
                    onClick = {
                        SortSplitKeywords.add(input)
                        input = ""
                    }
                ) {
                    Text(getString(R.string.sort_split_add))
                }
            }
        }
    }

    /** 空格等不可见关键词用可见占位，避免用户看不出标签内容。 */
    private fun labelOf(keyword: String): String {
        return if (keyword.isBlank()) {
            getString(R.string.sort_split_space)
        } else {
            keyword
        }
    }

    @Composable
    private fun ColumnScope.SectionTitle(text: String, color: Color, topPadding: Dp = 0.dp) {
        Text(
            text = text,
            modifier = Modifier.padding(top = topPadding),
            color = color,
            fontWeight = FontWeight.Bold,
            style = plainTextStyle(15.sp),
        )
    }
}
