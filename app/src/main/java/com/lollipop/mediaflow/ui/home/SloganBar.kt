package com.lollipop.mediaflow.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/** Slogan 图标尺寸，对齐 View 版 item_home_slogan（128dp x 42dp，内部上下留 4dp）。 */
private val SloganIconWidth = 128.dp
private val SloganIconHeight = 34.dp
private val SloganBarHeight = 42.dp

/** 胶囊圆角：固定为单行高度的一半（21dp），内容折行变高后圆角视觉保持一致。 */
private val SloganBarCornerRadius = SloganBarHeight / 2

/** 胶囊形状：固定圆角，避免使用 CircleShape 时圆角随高度变大。 */
private val SloganBarShape = RoundedCornerShape(SloganBarCornerRadius)

/** 胶囊左右内边距：让图标不贴到胶囊的圆角上。 */
private val SloganBarPaddingHorizontal = 12.dp

/** Slogan 文本的垂直内边距：多行时不至于贴住上下圆角边。 */
private val SloganBarPaddingVertical = 10.dp

/** 胶囊阴影高度，与底部 Tab 胶囊保持一致。 */
private val SloganBarElevation = 8.dp

/**
 * 顶部 Slogan 胶囊：居左展示，始终可见（自定义文本优先，否则展示 App 图标）。
 *
 * 胶囊底色与页面背景色一致，仅靠阴影浮起；点击展开操作气泡菜单，长按触发隐私模式
 * （由上层做身份验证）。
 *
 * [reloadTick] 变化时会重新读取偏好设置，保证从设置页返回后 Slogan 及时更新。
 */
@Composable
fun SloganBar(
    reloadTick: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customValue = remember(reloadTick) {
        Preferences.customSloganValue.get()
    }
    val themeColor = currentThemeColor()

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            // 高度只保底单行高度（42dp），内容折行时交由文本撑高，不再锁死高度
            .heightIn(min = SloganBarHeight)
            // 不设最大宽度：短文本贴合内容并收缩为正圆，长文本自然撑满可用宽度后换行
            .widthIn(min = SloganBarHeight)
            .shadow(elevation = SloganBarElevation, shape = SloganBarShape)
            .clip(SloganBarShape)
            .background(themeColor.windowBackground)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = SloganBarPaddingHorizontal),
        contentAlignment = Alignment.CenterStart
    ) {
        if (customValue.isNotEmpty()) {
            Text(
                text = customValue,
                color = themeColor.buttonText,
                style = plainTextStyle(16.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = SloganBarPaddingVertical)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_mediaflow),
                contentDescription = null,
                colorFilter = ColorFilter.tint(themeColor.buttonText),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(SloganIconWidth)
                    .height(SloganIconHeight)
            )
        }
    }
}
