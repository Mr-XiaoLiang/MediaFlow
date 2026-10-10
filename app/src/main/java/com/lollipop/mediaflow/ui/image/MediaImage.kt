package com.lollipop.mediaflow.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFramePercent

/** 视频封面默认取帧位置（0..1）。 */
const val DefaultVideoFramePercent = 0.1

/**
 * 统一的图片加载组件（Coil 3）。
 *
 * 替代旧的 [`CoverLoader`]（ImageView + `override`）与 Glide 用法：
 * - **尺寸显式表达**：[targetSize] 直接映射到 `ImageRequest.size()`。缩略图与模糊背景务必传小值，
 *   这是内存占用的第一控制点（旧实现靠 `override(w, h)`）；
 * - **视频封面**：由 [VideoFrameDecoder] 解码，取帧位置用 [videoFramePercent]；
 * - **远程图片**：由 [OkHttpNetworkFetcherFactory] 走 OkHttp（WebDAV 接入后即可直接使用）；
 * - **加载状态**：通过 [onState] 暴露，便于页面在首帧到达前盖封面 / 显示占位。
 *
 * 注意：视频帧与网络组件需在 Application 的 ImageLoader 中安装，见 [installFlowComponents]。
 */
@Composable
fun MediaImage(
    /** Coil model：`Uri` / `String`（含 `content://`、`file://`、`http(s)://`）/ `File` / `MediaInfo.File` 等。 */
    data: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    /** 目标解码尺寸（像素）；null 表示按原始尺寸解码。 */
    targetSize: IntSize? = null,
    /** 视频封面取帧位置（0..1），仅对视频生效。 */
    videoFramePercent: Double = DefaultVideoFramePercent,
    onState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current
    val model = remember(context, data, targetSize, videoFramePercent) {
        ImageRequest.Builder(context)
            .data(data)
            .apply {
                targetSize?.let { size ->
                    size(size.width, size.height)
                }
            }
            .videoFramePercent(videoFramePercent)
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onState = onState
    )
}

/**
 * 在 Application 的 [ImageLoader] 中安装本项目需要的组件。
 *
 * - [VideoFrameDecoder]：视频文件 / 流的封面取帧（Coil 3 起视频解码拆分为 `coil-video`）；
 * - [OkHttpNetworkFetcherFactory]：远程图片（`coil-network-okhttp`），与项目 OkHttp 版本一致。
 */
fun ImageLoader.Builder.installFlowComponents(): ImageLoader.Builder {
    return components {
        add(VideoFrameDecoder.Factory())
        add(OkHttpNetworkFetcherFactory())
    }
}
