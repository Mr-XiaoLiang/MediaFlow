package com.lollipop.mediaflow.ui.image

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.RequestBuilderTransform
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.lollipop.mediaflow.BuildConfig
import com.lollipop.mediaflow.tools.ImageLoadProbe

/**
 * 解码位图的**最大**尺寸（像素）。
 *
 * 沿用旧 View 版 `CoverLoader` 的硬上限（原实现是 `override(viewWidth.coerceAtMost(1440), ...)`）：
 * 无论调用方给出多大的尺寸，都不会解出超过该上限的位图，避免"列表里读了一张 4000×3000"。
 */
const val MaxImageWidth = 1440
const val MaxImageHeight = 3168

/**
 * 本项目**唯一**的 Glide 参数收口点。
 *
 * 所有图片加载（本地图片 / 本地视频封面 / 将来的远程图）都汇聚到这里；调用方只关心
 * 「资源（[MediaImage] 的 data）+ 尺寸（targetSize）+ 展示方式（contentScale）」。
 * 将来要调策略或换实现，只改这一个文件即可。
 */
private val FlowImageOptions: RequestOptions = RequestOptions()
    // 默认值本就是 AUTOMATIC，这里显式写出是为了**把语义钉住**、避免被后续改动无声改变：
    // - 远程数据 → 缓存「源」（DATA）；
    // - 本地数据 → 缓存「解码后的结果」（RESOURCE，落盘）。
    // 后者是本地媒体列表的关键：内存缓存被淘汰后可直接读磁盘上的缩略图，
    // 而不必回到几 MB / 几 GB 的原文件重新解码、重新抽帧。
    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)

/**
 * 统一的图片加载组件（Glide）。
 *
 * ## 尺寸策略（重要）
 * 尺寸会**参与 Glide 的缓存 key**（`EngineKey` = model + signature + width + height + ...），
 * 所以尺寸一旦波动就会导致"反复加载、缓存不命中"：
 * - 传了 [targetSize]：用 [RequestBuilder.override] 把解码尺寸**钉死**，请求也不必等待 Compose 布局，
 *   可以更早发起；同时按 [MaxImageWidth] / [MaxImageHeight] 夹逼，控制单张图的内存上限；
 * - 没传：交给 Glide 从 Compose 的布局约束推断。此时**务必保证尺寸不依赖内容**
 *   （否则 Glide 会退化成 `SIZE_ORIGINAL`，解出超大图）。
 *
 * ## 视频封面
 * 不做特殊处理，直接交给 Glide 的 `VideoDecoder`：其默认帧（`DEFAULT_FRAME = -1`）取的是
 * 代表性帧、不需要 seek，是最快的路径；视频封面同样会被 `AUTOMATIC` 策略落盘。
 *
 * 注意：Glide 无需在 Application 中做初始化；`Glide.with(context)` 首次调用即自举。
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MediaImage(
    /** Glide model：`Uri` / `String`（含 `content://`、`file://`、`http(s)://`）/ `File` 等。 */
    data: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    /** 目标解码尺寸（像素）。建议传「该媒体的显示尺寸」（与显示比例一致），以获得最优的内存与缓存命中。 */
    targetSize: IntSize? = null,
) {
    // 必须 remember：`requestBuilderTransform` 参与 GlideImage 内部 `remember` 的 key，
    // 若每次重组都新建 lambda，会导致请求被反复重建（这正是"尺寸/参数不稳 → 反复加载"的一种成因）。
    val transform: RequestBuilderTransform<Drawable> = remember(targetSize) {
        { builder: RequestBuilder<Drawable> ->
            builder
                .apply(FlowImageOptions)
                .applyDebugProbe()
                .applySize(targetSize)
        }
    }
    GlideImage(
        model = data,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        requestBuilderTransform = transform,
    )
}

/** 把 [targetSize] 钉进请求，并夹逼到全局上限。 */
private fun RequestBuilder<Drawable>.applySize(size: IntSize?): RequestBuilder<Drawable> {
    if (size == null) return this
    return override(
        size.width.coerceIn(1, MaxImageWidth),
        size.height.coerceIn(1, MaxImageHeight),
    )
}

/** 【临时诊断】仅 Debug 构建挂监听，用于验证缓存命中来源；Release 零开销。 */
private fun RequestBuilder<Drawable>.applyDebugProbe(): RequestBuilder<Drawable> {
    if (!BuildConfig.DEBUG) return this
    return listener(ImageLoadProbe)
}
