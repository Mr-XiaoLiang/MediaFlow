package com.lollipop.mediaflow.page.flow.compose.photo

import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.lollipop.mediaflow.ui.image.MediaImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 全屏图片预览（含共享元素式进出场动画）。
 *
 * ## 布局层级
 * 本组件由页面的浮层插槽承载（[com.lollipop.mediaflow.ui.BasicFlowComposeActivity.Overlay]），
 * 即位于 `FlowScaffold` **之外**、铺满整屏：不随侧栏让位收窄，触摸也不会漏给侧栏
 * （对齐旧实现把面板挂在 `android.R.id.content` 上的做法）。
 *
 * ## 动画算法（与旧 [com.lollipop.mediaflow.ui.PhotoFullPreviewDelegate] 同口径）
 * 旧实现把源 item 的 View 整体缩放平移；这里改为对「容器内的一份 `ContentScale.Fit` 绘制」
 * 做同样的缩放平移。
 *
 * 几何**完全解析**，不依赖任何子节点的测量结果：
 * - **终点** = 图片在容器内的 Fit 矩形（与 [SubsamplingScaleImageView] 的默认显示
 *   `SCALE_TYPE_CENTER_INSIDE` 完全一致，切换无跳变）；
 * - **起点** = 把该 Fit 矩形映射回源 item 的实测矩形（`left` / `top` / `width`）；
 * - 变换语义固定为 `screen = translation + scale * local`（缩放基点 = 图层左上角），
 *   起点、终点各有解析解，中间按进度线性插值。
 *
 * 为何不用「与 item 等尺寸的载体 + 中心基点」：列表项宽恒为屏宽、高 = 宽 ÷ 比例，
 * 横屏时高度普遍超过屏高，载体尺寸会被父容器的 max 约束截断；而中心基点又由载体尺寸
 * 决定，于是终点中心会算到屏幕之外——表现为「向上缩小飞出屏幕，再闪一下出现在中间」。
 *
 * 起点取源 item 的**完整矩形**（不裁剪到屏幕内）：item 部分在屏幕外时，那部分在动画期间
 * 也仍在屏幕外，屏幕内可见部分与源严格重合。
 *
 * ## 载体 → 大图的交接
 * 入场动画到位后**先放行大图**（`isImageRevealed`），等它 `onReady` 并真正绘制出一帧后，
 * 才移除 Compose 载体（`isImageShown`）。顺序不可颠倒：先移除载体会留下「两边都没画」的
 * 空档，观感就是动画结束瞬间闪一下；反之若在解码完成时就放行可见性，又会抢在动画前面
 * 先画出「整图缩小居中」的一帧。两个时机分别由「动画进度」与「View 就绪」控制。
 *
 * ⚠️ [origin] 必须是**组合期实时读取**的最新矩形，不能是点击瞬间的快照：本工程的 Activity
 * 声明了 `configChanges`，转屏不重建，源 item 会按新方向重新布局；若沿用旧矩形，
 * 收起动画会飞到错误的位置（横屏尤其明显）。
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
    val scope = rememberCoroutineScope()

    /** 进出场进度：0 = 停在源 item 处，1 = 全屏展示。只在绘制阶段读取。 */
    val progress = remember { Animatable(0F) }

    /** 大图 View（入场完成后接管显示）。 */
    var imageView by remember { mutableStateOf<SubsamplingScaleImageView?>(null) }

    /** 大图是否已解码就绪（尺寸已知、可安全绘制）。由 `onReady` 置位。 */
    var isImageReady by remember { mutableStateOf(false) }

    /** 大图 View 是否已放行显示：入场动画到位后才置位，动画期间保持隐藏。 */
    var isImageRevealed by remember { mutableStateOf(false) }

    /** 载体是否已让位（大图真正画出一帧之后才置位，避免出现「两边都没画」的空档）。 */
    var isImageShown by remember { mutableStateOf(false) }

    /** 是否已做过入场决策（几何测量完成前不允许「无动画直达」的兜底生效）。 */
    var hasEntered by remember { mutableStateOf(false) }

    /** 本层在根坐标系中的偏移：用于把根坐标系的 [origin] 换算成本层局部坐标。 */
    var selfOffset by remember { mutableStateOf(Offset.Zero) }

    /** 本层尺寸（px）。 */
    var containerSize by remember { mutableStateOf(Size.Zero) }

    // ---- 几何（与旧 updateAnchor 同口径，但完全解析）----
    val localOrigin = origin?.translate(-selfOffset.x, -selfOffset.y)
    val itemRect = localOrigin ?: Rect.Zero

    // 源 item 的位置与宽度：全部取实测。高度不参与计算——起点映射只需要宽度与比例，
    // 这样即使 item 高于屏幕（或部分滚出视口），也不会因为「实测高度被裁剪」而失准。
    val itemLeft = itemRect.left
    val itemTop = itemRect.top
    val itemWidth = itemRect.width

    /** 源几何是否可用（宽度为零时无法推导起点，只能走退化路径）。 */
    val hasOrigin = itemWidth > 0F

    /**
     * 图片在容器内按 `ContentScale.Fit` 显示时的矩形。
     *
     * 它与 [SubsamplingScaleImageView] 的默认显示（`SCALE_TYPE_CENTER_INSIDE`）一致，
     * 因此既是入场动画的终点，也是大图接管后的视觉位置——两者天然对齐，切换无跳变。
     * 该矩形由容器尺寸与图片比例**解析**得出，不读取任何子节点尺寸。
     */
    val fitRect: Rect? = if (hasOrigin && ratio != null && ratio > 0F &&
        containerSize.width > 0F && containerSize.height > 0F
    ) {
        val fitWidth = minOf(containerSize.width, containerSize.height * ratio)
        val fitHeight = fitWidth / ratio
        Rect(
            offset = Offset(
                x = (containerSize.width - fitWidth) / 2F,
                y = (containerSize.height - fitHeight) / 2F
            ),
            size = Size(fitWidth, fitHeight)
        )
    } else {
        null
    }

    /** 几何是否齐备：源矩形、图片比例、容器尺寸都可用。 */
    val isGeometryReady = fitRect != null

    // 起点变换：把「容器内的 Fit 矩形」映射回「源 item 矩形」。
    // 变换语义是 `screen = translation + scale * local`（基点 = 图层左上角），
    // 所以只需让 fitRect 的左上角落到 (itemLeft, itemTop)、宽度落到 itemWidth。
    val startScale = if (fitRect != null) itemWidth / fitRect.width else 1F
    val startTranslationX = if (fitRect != null) itemLeft - fitRect.left * startScale else 0F
    val startTranslationY = if (fitRect != null) itemTop - fitRect.top * startScale else 0F

    /** 收起：反向跑位移，动画结束后通知外层移除浮层。 */
    fun exit() {
        // 先让载体接管（它此刻正停在大图的显示位置），再隐藏大图——顺序不能反，
        // 否则中间会有一帧「载体还没画、大图已经不画」的空档。
        isImageShown = false
        isImageRevealed = false
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
            isImageRevealed = true
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
        // ---- 交接：Compose 载体 → 大图 View ----
        // 顺序是「先显示、后隐藏」：
        // ① 先放行大图（此刻它的显示矩形与载体完全重合，重叠一帧无感）；
        // ② 等它解码就绪（onReady），再等两帧让它真正画出来；
        // ③ 最后才移除载体。
        // 反过来「先移除载体」必定留下一帧空档，观感就是动画结束瞬间闪一下。
        isImageRevealed = true
        withTimeoutOrNull(PreviewHandoverTimeoutMs) {
            snapshotFlow { isImageReady }.first { it }
        }
        withFrameNanos { }
        withFrameNanos { }
        isImageShown = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 自身位置（根坐标系）→ 把 origin 换算成本层局部坐标。
            // 用 positionInRoot 而不是 boundsInRoot：后者会把「祖先裁剪链」也算进去。
            .onGloballyPositioned { coordinates -> selfOffset = coordinates.positionInRoot() }
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

        // 动画载体：容器尺寸的一份 Fit 绘制，靠变换在「源 item 矩形」与「大图显示矩形」之间过渡。
        // 尺寸恒等于容器，因此不存在被父约束截断的问题，也不依赖任何布局测量结果。
        //
        // ⚠️ 必须等几何就绪再渲染：容器测量的那一帧 `containerSize` 还是 0，
        // `fitRect` 为 null，此时 `startScale = 1`、`startTranslation = 0`，
        // 载体就会以「整图缩小居中」的样子被画出来——观感是「先闪一下，再开始动画」。
        if (!isImageShown && isGeometryReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = progress.value
                        val scale = startScale + (1F - startScale) * fraction
                        scaleX = scale
                        scaleY = scale
                        translationX = startTranslationX * (1F - fraction)
                        translationY = startTranslationY * (1F - fraction)
                        // 基点固定为图层左上角：位置完全由 translation 解析给出，与图层尺寸无关
                        transformOrigin = TransformOrigin(0F, 0F)
                    }
            ) {
                MediaImage(
                    data = uri,
                    modifier = Modifier.fillMaxSize(),
                    // 与列表项同一语义（Fit）；容器尺寸与原图上限一致，解码开销可控
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // 大图：整屏，入场动画到位并完成交接后接管显示
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isImageRevealed) 1F else 0F },
            factory = { context ->
                SubsamplingScaleImageView(context).apply {
                    setOnClickListener { requestDismiss() }
                    setOnImageEventListener(object : SubsamplingScaleImageView.OnImageEventListener {
                        override fun onReady() {
                            // 图片尺寸已知、内容可绘制：交接流程可以安全推进
                            isImageReady = true
                        }

                        override fun onImageLoaded() = Unit

                        override fun onPreviewLoadError(e: Exception) = Unit

                        override fun onImageLoadError(e: Exception) = Unit

                        override fun onTileLoadError(e: Exception) = Unit

                        override fun onPreviewReleased() = Unit
                    })
                }.also { imageView = it }
            },
            update = { view ->
                imageView = view
                // 入场期间让 View **自身**不可见（不只依赖图层 alpha）：大图解码完成时
                // 若已可见，它会抢在动画前面先画出一帧。放行时机由 `isImageRevealed` 控制，
                // 即「入场动画到位」而不是「解码完成」。
                view.visibility = if (isImageRevealed) View.VISIBLE else View.INVISIBLE
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

/**
 * 交接时等待大图解码就绪的上限（毫秒）。
 *
 * 正常本地图片远快于此；超时（例如异常文件）则不再等待，直接完成交接，
 * 避免界面一直停在载体上而失去大图的缩放交互。
 */
private const val PreviewHandoverTimeoutMs = 1500L

/** 判定「是否处于放大态」的阈值：缩放接近 `minScale` 时视为整图，无需先复位。 */
private const val ScaleResetThreshold = 1.01F

/** 预览背景（对齐旧实现 `BACKGROUND_COLOR = 0xCC000000`）。 */
private val PreviewScrim = Color(0xCC000000)
