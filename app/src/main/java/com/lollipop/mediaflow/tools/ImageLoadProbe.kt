package com.lollipop.mediaflow.tools

import android.graphics.drawable.Drawable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog

/**
 * 【临时诊断件，非业务代码，排查完成后可整段删除】
 *
 * Glide 图片加载探针。挂在 [com.lollipop.mediaflow.ui.image.MediaImage] 的请求上（仅 Debug），
 * 用来**用真实数据验证**加载来源与缓存命中情况，而不是靠推理：
 *
 * - `LOCAL`             → 真的回原文件读取并重新解码（**未命中任何缓存**）；
 * - `RESOURCE_DISK_CACHE` → 命中「派生结果」的磁盘缓存（**本地媒体二次访问的理想结果**）；
 * - `DATA_DISK_CACHE`   → 命中「源」的磁盘缓存（远程图才有）；
 * - `MEMORY_CACHE`      → 命中内存缓存。
 *
 * 过滤：`adb logcat -s Lollipop | grep ImageLoadProbe`
 *
 * 验收看点：同一批本地视频第二次进入视口时，应当由 `LOCAL` 变为
 * `RESOURCE_DISK_CACHE` / `MEMORY_CACHE`，而不是再次 `LOCAL`。
 */
object ImageLoadProbe : RequestListener<Drawable> {

    private val log = registerLog()

    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<Drawable>,
        isFirstResource: Boolean,
    ): Boolean {
        log.e("FAILED model=$model first=$isFirstResource", e)
        return false
    }

    override fun onResourceReady(
        resource: Drawable,
        model: Any,
        target: Target<Drawable>,
        dataSource: DataSource,
        isFirstResource: Boolean,
    ): Boolean {
        log.i(
            "OK source=$dataSource first=$isFirstResource" +
                " size=${resource.intrinsicWidth}x${resource.intrinsicHeight} model=$model"
        )
        return false
    }
}
