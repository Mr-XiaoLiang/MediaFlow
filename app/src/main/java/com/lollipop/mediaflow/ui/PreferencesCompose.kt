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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.ui.theme.currentThemeColor


fun LazyListScope.PreferencesGroupItem(
    key: Any? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    item(key = key) {
        PreferencesGroup(
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
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .background(color = currentThemeColor().preferencesGroup),
        content = content,
        horizontalAlignment = Alignment.CenterHorizontally
    )
}

@Composable
fun PreferencesDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    )
}

@Composable
fun PreferencesSwitch(
    name: String,
    summary: String,
    isChecked: Boolean,
    onCheckedChange: (isCheck: Boolean) -> Unit
) {
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

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
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