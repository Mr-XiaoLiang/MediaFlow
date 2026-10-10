package com.lollipop.mediaflow.page.flow.compose.photo

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.lollipop.mediaflow.ui.image.MediaImage
import kotlinx.coroutines.launch

/**
 * 全屏图片预览（含共享元素式进出场动画）。
 *
 * ## 布局层级
 * 本组件由页面的浮层插槽承载（[com.lollipop.mediaflow.ui.BasicFlowComposeActivity.Overlay]），
 * 即位于 `FlowScaffold` **之外**、铺满整屏：不随侧栏让位收窄，触摸也不会漏给侧栏
 * （对齐旧实现把面板挂在 `android.R.id.content` 上的做法）。
 *
 * ## 动画算法（与旧 [com.lollipop.mediaflow.ui.PhotoFullPreviewDelegate] 同口径）
 * 旧实现把源 item 的 View 整体缩放平移；这里用「与源 item 等尺寸的一份完整绘制」代替
 * （布局尺寸 = item 实测尺寸、`ContentScale.Fit`，因此 Coil 命中的就是列表项那一份缓存）。
 *
 * 几何完全对齐旧 `updateAnchor`：
 * - `scale = min(容器宽 / item 宽, 容器高 / item 高)`——源 item 宽恒为屏宽，故实际只可能
 *   **长图缩小到适合屏幕、短图保持原尺寸**；
 * - 位移只负责把「item 的中心」移到「容器的中心」（缩放围绕中心，故与 `scale` 无关）。
 *
 * 起点取源 item 的**完整矩形**（不裁剪到屏幕内）：item 部分在屏幕外时，那部分在动画期间
 * 也仍在屏幕外，屏幕内可见部分与源严格重合。
 *
 * 动画结束后把显示交给 [SubsamplingScaleImageView]（此时两者的视觉矩形完全相同，切换无跳变）。
 *
 * @param uri    图片地址。
 * @param ratio  源 item 的显示比例（宽 / 高）；`null` 表示元数据未就绪，退化为纯切换。
 * @param origin 源 item 在**根坐标系**中的矩形；`null` 表示拿不到，退化为纯切换。
 */
@Composable
fun FlowPhotoPreview(
    uri: Uri,
    ratio: Float?,
    origin: Rect?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    /** 进出场进度：0 = 停在源 item 处，1 = 全屏展示。只在绘制阶段读取。 */
    val progress = remember { Animatable(0F) }

    /** 大图 View（入场完成后接管显示）。 */
    var imageView by remember { mutableStateOf<SubsamplingScaleImageView?>(null) }

    /** 大图是否已接管显示（一次性状态，避免逐帧重组）。 */
    var isImageShown by remember { mutableStateOf(false) }

    /** 是否已做过入场决策（几何测量完成前不允许「无动画直达」的兜底生效）。 */
    var hasEntered by remember { mutableStateOf(false) }

    /** 本层在根坐标系中的偏移：用于把根坐标系的 [origin] 换算成本层局部坐标。 */
    var selfOffset by remember { mutableStateOf(Offset.Zero) }

    /** 本层尺寸（px）。 */
    var containerSize by remember { mutableStateOf(Size.Zero) }

    // ---- 几何（与旧 updateAnchor 同口径）----
    val localOrigin = origin?.translate(-selfOffset.x, -selfOffset.y)
    val itemRect = localOrigin ?: Rect.Zero

    // 源 item 的位置与尺寸。
    // ⚠️ 高度必须由「宽度 ÷ 比例」**推导**，不能取实测高度：item 高于屏幕（或部分滚出视口）时，
    // 实测值可能只反映可见部分，会把「200dp 高的图当成 100dp」参与计算。
    // `left` / `top` / `width` 仍取实测——它们是布局位置，不受裁剪影响。
    val itemLeft = itemRect.left
    val itemTop = itemRect.top
    val itemWidth = itemRect.width
    val itemHeight = if (ratio != null && ratio > 0F) {
        itemWidth / ratio
    } else {
        itemRect.height
    }

    /** 源几何是否可用（缺失或尺寸为零时无法推导起点，只能走退化路径）。 */
    val hasOrigin = itemWidth > 0F && itemHeight > 0F
    /** 几何是否齐备：源几何可用且本层已完成测量。 */
    val isGeometryReady = hasOrigin && containerSize.width > 0F && containerSize.height > 0F

    /** 终点缩放：整图在容器内等比铺满的最大尺寸（= 大图 View 的默认显示）。 */
    val endScale = if (isGeometryReady) {
        minOf(containerSize.width / itemWidth, containerSize.height / itemHeight)
    } else {
        1F
    }

    // 缩放围绕**中心**，因此位移只需把 item 中心移到容器中心：
    // 起点 = item 所在位置，终点 = 容器中心 − item 半径（与 scale 无关）。
    val endTranslationX = containerSize.width / 2F - itemWidth / 2F
    val endTranslationY = containerSize.height / 2F - itemHeight / 2F

    /** 收起：反向跑位移，动画结束后通知外层移除浮层。 */
    fun exit() {
        isImageShown = false
        scope.launch {
            progress.animateTo(
                targetValue = 0F,
                animationSpec = tween(
                    durationMillis = PreviewDurationMs,
                    easing = FastOutSlowInEasing
                )
            )
            onDismiss()
        }
    }

    /** 关闭请求：处于放大态时先把大图动画回整图（旧版两段式），避免从放大画面直接飞回。 */
    fun requestDismiss() {
        val view = imageView
        if (view == null || view.scale <= view.minScale * ScaleResetThreshold) {
            exit()
            return
        }
        view.animateScaleAndCenter(view.minScale, view.center)
            ?.withDuration(PreviewDurationMs.toLong())
            ?.withOnAnimationEventListener(object : SubsamplingScaleImageView.OnAnimationEventListener {
                override fun onComplete() = exit()

                override fun onInterruptedByUser() = exit()

                override fun onInterruptedByNewAnim() = Unit
            })
            ?.start()
    }

    // 返回键关闭（与点击画面同一路径，因此同样带动画）
    BackHandler { requestDismiss() }

    // 入场：只决策一次。
    // 顺序很关键——几何测量完成前不能走「无动画」分支（否则会先直达全屏、动画无从播放）。
    LaunchedEffect(isGeometryReady, ratio) {
        if (hasEntered) {
            return@LaunchedEffect
        }
        if (ratio == null || !hasOrigin) {
            // 比例或源矩形缺失：中间态无法与源保持一致，直接进入全屏展示
            hasEntered = true
            progress.snapTo(1F)
            isImageShown = true
            return@LaunchedEffect
        }
        if (!isGeometryReady) {
            // 还差本层测量：等 onGloballyPositioned / onSizeChanged 补齐后重新进入本效应
            return@LaunchedEffect
        }
        hasEntered = true
        progress.animateTo(
            targetValue = 1F,
            animationSpec = tween(
                durationMillis = PreviewDurationMs,
                easing = FastOutSlowInEasing
            )
        )
        isImageShown = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 自身位置（根坐标系）→ 把 origin 换算成本层局部坐标
            .onGloballyPositioned { selfOffset = it.boundsInRoot().topLeft }
            .onSizeChanged {
                containerSize = Size(it.width.toFloat(), it.height.toFloat())
            }
            // 点击画面关闭；大图的缩放 / 平移由 SubsamplingScaleImageView 自行消费
            .clickable { requestDismiss() }
    ) {
        // 压暗背景：alpha 随进度（绘制阶段读取，不触发重组）。
        // ⚠️ `graphicsLayer` 必须写在 `background` **之前**：链中越靠前越靠外，
        // 图层只有包住背景绘制，其 alpha 才会作用到压暗层上（否则表现为「一进预览就纯黑」）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isImageShown) 1F else progress.value }
                .background(PreviewScrim)
        )

        // 动画载体：与源 item 等尺寸的一份完整绘制。
        // 起点在 item 的屏幕位置（可能部分在屏幕外），终点为「等比缩小 / 放大后居中」的位置。
        if (!isImageShown) {
            Box(
                modifier = Modifier
                    .width(with(density) { itemWidth.toDp() })
                    .height(with(density) { itemHeight.toDp() })
                    .graphicsLayer {
                        val fraction = progress.value
                        val scale = 1F + (endScale - 1F) * fraction
                        scaleX = scale
                        scaleY = scale
                        translationX = itemLeft + (endTranslationX - itemLeft) * fraction
                        translationY = itemTop + (endTranslationY - itemTop) * fraction
                    }
            ) {
                MediaImage(
                    data = uri,
                    modifier = Modifier.fillMaxSize(),
                    // 与列表项同一语义（Fit）与同一尺寸 ⇒ 命中同一份 Coil 缓存
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // 大图：整屏，入场完成后接管显示
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isImageShown) 1F else 0F },
            factory = { context ->
                SubsamplingScaleImageView(context).apply {
                    setOnClickListener { requestDismiss() }
                }.also { imageView = it }
            },
            update = { view ->
                imageView = view
                if (view.tag != uri) {
                    view.tag = uri
                    view.resetScaleAndCenter()
                    view.setImage(ImageSource.uri(uri))
                }
            }
        )
    }
}

/** 进出场动画时长（对齐旧实现 `DURATION = 300L`，缓动统一为 iOS 风格慢入慢出）。 */
private const val PreviewDurationMs = 300

/** 判定「是否处于放大态」的阈值：缩放接近 `minScale` 时视为整图，无需先复位。 */
private const val ScaleResetThreshold = 1.01F

/** 预览背景（对齐旧实现 `BACKGROUND_COLOR = 0xCC000000`）。 */
private val PreviewScrim = Color(0xCC000000)
