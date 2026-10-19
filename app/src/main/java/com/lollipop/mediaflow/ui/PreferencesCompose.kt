package com.lollipop.mediaflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.ui.theme.currentThemeColor


/** 分组标题字号（弱化处理，不与分组内正文抢视线）。 */
private val GroupTitleFontSize = 13.sp

/** 分组标题不透明度（弱化处理）。 */
private const val GroupTitleAlpha = 0.6F

/**
 * 偏好设置分组条目（可选标题 + 卡片内容）。
 *
 * @param titleRes 分组标题的字符串资源 id；为 `null` 时不渲染标题（保持无标题样式）。
 *   之所以传资源 id 而非已解析的字符串：本函数运行在 `LazyListScope`（**非 Composable**）作用域，
 *   标题必须在 `item { }` 内部才可解析。
 */
fun LazyListScope.PreferencesGroupItem(
    key: Any? = null,
    titleRes: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    item(key = key) {
        PreferencesGroup(
            title = titleRes?.let { stringResource(it) },
            modifier = if (key != null) {
                Modifier.animateItem()
            } else {
                Modifier
            },
            content = content
        )
    }
}

@Composable
fun PreferencesGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // 标题位于卡片之上，用于点明本组含义（此前分组无标题，意义不明）
        if (title != null) {
            Text(
                text = title,
                color = currentThemeColor().buttonText.copy(alpha = GroupTitleAlpha),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                fontSize = GroupTitleFontSize,
                fontWeight = FontWeight.Medium
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(color = currentThemeColor().preferencesGroup),
            content = content,
            horizontalAlignment = Alignment.CenterHorizontally
        )
    }
}

@Composable
fun PreferencesDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    )
}

/** 开关不可用时的内容不透明度（弱化处理，仍保持可见以提示功能存在）。 */
private const val DisabledSwitchContentAlpha = 0.5F

/**
 * 开关型偏好项。
 *
 * @param enabled 是否可交互。为 `false` 时开关不可切换且文字弱化，
 *   用于「功能已展示但当前条件尚不满足」的场景（如仅暗码模式需先通过暗码进入过一次）。
 */
@Composable
fun PreferencesSwitch(
    name: String,
    summary: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (isCheck: Boolean) -> Unit
) {
    val contentColor = if (enabled) {
        currentThemeColor().buttonText
    } else {
        currentThemeColor().buttonText.copy(alpha = DisabledSwitchContentAlpha)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1F)
        ) {
            Text(
                text = name,
                color = contentColor,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 16.sp
            )
            Text(
                text = summary,
                color = contentColor,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 12.sp
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
fun PreferencesIntent(
    name: String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1F)
        ) {
            Text(
                text = name,
                color = currentThemeColor().buttonText,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 16.sp
            )
            Text(
                text = summary,
                color = currentThemeColor().buttonText,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null
        )
    }
}

/**
 * 分段选择项：用于「模式 / 档位」这类少量枚举值的选择。
 *
 * 浮点参数一律以档位形式暴露，因此不再使用滑块；每档默认值由调用方给出。
 */
@Composable
fun <T> PreferencesSegmented(
    name: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = name,
            color = currentThemeColor().buttonText,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 16.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1F)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            color = if (isSelected) {
                                currentThemeColor().buttonSlider
                            } else {
                                currentThemeColor().buttonMask
                            }
                        )
                        .clickable { onSelect(option) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelOf(option),
                        color = currentThemeColor().buttonText,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 说明文本：用于分组内的辅助说明（如当前模式的效果说明）。 */
@Composable
fun PreferencesSummary(text: String) {
    Text(
        text = text,
        color = currentThemeColor().buttonText,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        fontSize = 12.sp
    )
}

@Composable
fun PreferencesSlide(
    name: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    value: Float,
    onValueChange: (value: Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = name,
            color = currentThemeColor().buttonText,
            fontSize = 16.sp
        )
        Slider(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            )
        )
    }
}