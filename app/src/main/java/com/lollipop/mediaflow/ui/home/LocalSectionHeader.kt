package com.lollipop.mediaflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.common.MediaSort
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.home.menu.ComposePopupMenuAnchor
import com.lollipop.mediaflow.ui.home.menu.ComposePopupMenuState
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * 本地资源标题行：左侧 “Local” 标记，右侧文件夹选择 / 排序方式菜单胶囊（图标形式）。
 */

/** 胶囊内按钮左右额外的可点区域：原按钮只有 28dp，手指很难点中。 */
private val MenuButtonHorizontalPadding = 10.dp

/** 胶囊内按钮上下额外的可点区域。 */
private val MenuButtonVerticalPadding = 5.dp

@Composable
fun LocalSectionHeader(
    page: HomePage,
    sortMenuState: ComposePopupMenuState,
    onFolderSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = currentThemeColor()
    val sortType = page.localState.sort.value
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.label_local),
            color = themeColor.buttonText,
            style = plainTextStyle(18.sp),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1F))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(themeColor.preferencesGroup)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onFolderSelect)
                    .padding(
                        horizontal = MenuButtonHorizontalPadding,
                        vertical = MenuButtonVerticalPadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.folder_open_24),
                    contentDescription = stringResource(R.string.content_desc_folder_select),
                    tint = themeColor.buttonText,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(4.dp)
                )
            }
            ComposePopupMenuAnchor(state = sortMenuState) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { sortMenuState.show() }
                        .padding(
                            horizontal = MenuButtonHorizontalPadding,
                            vertical = MenuButtonVerticalPadding
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(sortIconRes(sortType)),
                        contentDescription = stringResource(R.string.content_desc_sort),
                        tint = themeColor.buttonText,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

private fun sortIconRes(sort: MediaSort): Int {
    return when (sort) {
        MediaSort.DateDesc -> R.drawable.clock_arrow_down_24
        MediaSort.DateAsc -> R.drawable.clock_arrow_up_24
        MediaSort.NameDesc -> R.drawable.text_arrow_down_24
        MediaSort.NameAsc -> R.drawable.text_arrow_up_24
        MediaSort.Random -> R.drawable.shuffle_24
    }
}
