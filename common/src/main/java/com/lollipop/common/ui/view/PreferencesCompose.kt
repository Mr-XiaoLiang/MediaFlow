package com.lollipop.common.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.lollipop.common.ui.theme.currentThemeColor


fun LazyListScope.PreferencesGroupItem(
    content: @Composable ColumnScope.() -> Unit
) {
    item {
        PreferencesGroup(content)
    }
}

@Composable
fun PreferencesGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
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