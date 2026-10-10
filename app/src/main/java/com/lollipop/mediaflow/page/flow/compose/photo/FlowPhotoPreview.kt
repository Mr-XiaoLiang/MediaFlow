package com.lollipop.mediaflow.page.flow.compose.photo

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

/**
 * 全屏图片预览。
 *
 * 计划第七条明确：[SubsamplingScaleImageView] 是**唯一长期保留**的 `AndroidView` 组件
 * （大图缩放 / 平移动画成熟，自绘成本高），这里只做桥接与关闭交互。
 *
 * 与旧实现（[com.lollipop.mediaflow.ui.PhotoFullPreviewDelegate]）的差异（已记台账 M6 系列）：
 * - 旧实现挂在 `android.R.id.content` 上用 `ValueAnimator` 做 300ms 进出场动画（含共享元素式位移），
 *   这里先用 Compose 覆盖层 + 淡入，**未复刻位移动画**；
 * - 旧实现用 `touchMaskView` 阻断穿透，Compose 覆盖层天然独占触摸；
 * - 点击画面或返回键关闭（与旧一致）。
 */
@Composable
fun FlowPhotoPreview(
    uri: Uri,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    // 返回键关闭（对齐旧实现通过 OnBackPressedCallback 拦截返回）
    BackHandler(onBack = onDismiss)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PreviewScrim)
            .clickable(onClick = onDismiss)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SubsamplingScaleImageView(context).apply {
                    setOnClickListener { onDismiss() }
                }
            },
            update = { view ->
                view.resetScaleAndCenter()
                view.setImage(ImageSource.uri(uri))
            }
        )
    }
}

/** 预览背景（对齐旧实现 `BACKGROUND_COLOR = 0xCC000000`）。 */
private val PreviewScrim = Color(0xCC000000)
