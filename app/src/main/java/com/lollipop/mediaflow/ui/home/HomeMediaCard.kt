package com.lollipop.mediaflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.ui.image.MediaImage
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * 首页媒体卡片：固定宽度卡片，宽高比由元数据决定（用于瀑布流错落展示）。
 * 复用 [LMedia.loadMetadata] 异步加载时长 / 尺寸，加载完成后自动重排。
 */

/** 时长标签字体，对齐 View 版 item_media_staggered.xml 的 `@font/roboto_medium`。 */
private val DurationFontFamily = FontFamily(
    Font(R.font.roboto_medium, FontWeight.Medium)
)

@Composable
fun HomeMediaCard(
    media: LMedia,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var metadata by remember(media) {
        mutableStateOf((media as? MediaInfo.File)?.metadata)
    }
    DisposableEffect(media) {
        media.loadMetadata(context) { result ->
            metadata = result
        }
        onDispose { }
    }

    val ratio = remember(metadata) {
        val width = (metadata?.width ?: 1).coerceAtLeast(1)
        var height = (metadata?.height ?: 1).coerceAtLeast(1)
        if (height < width) {
            height = width
        }
        if (height * 9F / 16F > width) {
            height = (width / 9F * 16F).toInt()
        }
        width.toFloat() / height.toFloat()
    }

    val duration = metadata?.durationFormat ?: ""
    val themeColor = currentThemeColor()

    Box(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(themeColor.buttonMask)
            .clickable(onClick = onClick)
    ) {
        MediaImage(
            data = media.uri,
            contentDescription = media.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            if (media.mediaType == MediaType.Video && metadata != null && duration.isNotEmpty()) {
                MediaTag(
                    text = duration,
                    fontFamily = DurationFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
            if (showLabel) {
                MediaTag(
                    text = media.name,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MediaTag(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
) {
    val themeColor = currentThemeColor()
    Text(
        text = text,
        modifier = modifier
            .padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(themeColor.buttonBackground)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        color = themeColor.buttonText,
        // 用 TextView 的度量，避免继承 BodyLarge 的 24sp 行高 / 0.5sp 字距
        style = plainTextStyle(11.sp),
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 远程来源行：来源名 + 下方横向卡片列表（“第一分页”数据）。
 * 目前 WebDAV 尚未接入数据，列表为空时整行不展示。
 */
@Composable
fun RemoteSourceRow(
    sourceName: String,
    items: List<LMedia>,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    onItemClick: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = sourceName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = currentThemeColor().buttonText,
            style = plainTextStyle(16.sp),
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            itemsIndexed(items) { index, media ->
                HomeMediaCard(
                    media = media,
                    modifier = Modifier.width(150.dp),
                    showLabel = showLabel,
                    onClick = { onItemClick(index) }
                )
            }
        }
    }
}
