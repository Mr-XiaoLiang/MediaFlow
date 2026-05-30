package com.lollipop.mediaflow.page.play

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.lollipop.common.ui.page.CustomOrientationActivity
import com.lollipop.common.ui.page.GuidelineInsetsHelper
import com.lollipop.common.ui.page.PageOrientation
import com.lollipop.common.ui.view.BlurHelper
import com.lollipop.mediaflow.data.ArchiveQuick
import com.lollipop.mediaflow.data.MediaLoader
import com.lollipop.mediaflow.data.MediaMetadata
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.databinding.ActivityVideoQuickPlayBinding
import com.lollipop.mediaflow.page.flow.VideoPlayHolder
import com.lollipop.mediaflow.tools.PIPHelper
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.PipVisibleFilter
import com.lollipop.mediaflow.video.VideoListener
import com.lollipop.mediaflow.video.VideoManager
import kotlinx.coroutines.launch


class VideoQuickPlayActivity : CustomOrientationActivity(), VideoPlayHolder.VideoTouchDisplay,
    VideoPlayHolder.DecorationVisibilityCallback {

    private val binding by lazy {
        ActivityVideoQuickPlayBinding.inflate(layoutInflater)
    }

    private val backBtnVisibleFilter by lazy {
        PipVisibleFilter(binding.backBtn)
    }

    private val titleVisibleFilter by lazy {
        PipVisibleFilter(binding.titleView)
    }

    private val tagVisibleFilter by lazy {
        PipVisibleFilter(binding.tagGroup)
    }

    private val videoHolder by lazy {
        VideoPlayHolder.create(layoutInflater)
    }

    private val videoListener = object : VideoListener {
        override fun onPlay() {
            onVideoPlay()
        }

        override fun onPause() {
            onVideoPause()
        }
    }

    private val videoManager by lazy {
        VideoManager(this).also {
            it.eventObserver.add(videoListener)
        }
    }

    private val guidelineInsetsHelper = GuidelineInsetsHelper()

    private var isDecorationShown = true

    private var metadataCache: MediaMetadata? = null

    private val pipActionAdapter = PIPHelper.registerPipActions(this) { action ->
        when (action) {
            PIPHelper.Action.PLAY -> videoManager.play()
            PIPHelper.Action.PAUSE -> videoManager.pause()
            PIPHelper.Action.PREVIOUS -> {
            }

            PIPHelper.Action.NEXT -> {
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        initInsetsListener()
        binding.backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.contentContainer.addView(
            createContentPanel(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        updateBlur()
        changeDecoration(true)
        val videoUri = intent.data
        if (videoUri != null) {
            loadVideo(videoUri)
        }
    }

    private fun loadVideo(videoUri: Uri) {
        val act = this
        lifecycleScope.launch {
            log.i("loadVideo: $videoUri")
            val mediaFile = MediaLoader.loadMediaFileSync(act, videoUri)
            log.i("loadVideo: ${mediaFile?.docId}")
            if (mediaFile != null) {
                MetadataLoader.load(act, mediaFile) { metadata ->
                    updateTitle(
                        titleValue = mediaFile.name,
                        dimensions = metadata?.dimensionsFormat ?: "",
                        size = mediaFile.sizeFormat,
                        format = mediaFile.suffix.uppercase(),
                        duration = metadata?.durationFormat ?: ""
                    )
                    metadataCache = metadata
                    updatePipParams()
                }
                videoManager.resetMediaList(listOf(mediaFile))
                videoHolder.onBind(mediaFile)
                videoHolder.onSelected(isDecorationShown)
                videoManager.play(0)
                videoHolder.onPipChanged(isInPictureInPictureMode)
            }
        }
    }

    private fun onVideoPlay() {
        updatePipParams()
    }

    private fun onVideoPause() {
        updatePipParams()
    }

    private fun updatePipParams() {
        val isPlaying = videoManager.isPlaying()
        val pipOption = PIPHelper.Option(
            hasPrev = false,
            hasNext = false,
            hasPlay = !isPlaying,
            hasPause = isPlaying
        )
        PIPHelper.setParams(this, metadataCache, pipOption)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBlur()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        backBtnVisibleFilter.onPipChanged(isInPictureInPictureMode)
        titleVisibleFilter.onPipChanged(isInPictureInPictureMode)
        tagVisibleFilter.onPipChanged(isInPictureInPictureMode)
        videoHolder.onPipChanged(isInPictureInPictureMode)
    }

    private fun updateBlur() {
        BlurHelper.bind(
            window,
            binding.blurTarget,
            binding.backBtnBlur,
        )
    }

    private fun changeDecoration(isVisibility: Boolean) {
        isDecorationShown = isVisibility
        titleVisibleFilter.base.setVisible(isVisibility)
        tagVisibleFilter.base.setVisible(isVisibility)
        backBtnVisibleFilter.base.setVisible(isVisibility)
    }

    override fun onResume() {
        super.onResume()
        backBtnVisibleFilter.preference.setVisible(Preferences.isShowBackBtn.get())
        titleVisibleFilter.preference.setVisible(Preferences.isShowTitle.get())
        tagVisibleFilter.preference.setVisible(Preferences.isShowTag.get())
    }

    private fun createContentPanel(): View {
        videoHolder.archiveEnable = false
        videoManager.changeView(null, videoHolder.videoPlayerView)
        videoHolder.onFocusChange(
            controller = videoManager,
            touchDisplay = this,
            decorationCallback = this
        )
        videoManager.eventObserver.setFocus(videoHolder.videoListener)
        return videoHolder.itemView
    }

    override fun onOrientationChanged(orientation: PageOrientation) {
        super.onOrientationChanged(orientation)
        videoHolder.resetScaleGesture()
    }

    private fun updateTitle(
        titleValue: CharSequence,
        dimensions: CharSequence,
        size: CharSequence,
        format: CharSequence,
        duration: CharSequence
    ) {
        binding.root.post {
            binding.titleView.text = titleValue
            binding.dimensionsTagView.text = dimensions
            binding.sizeTagView.text = size
            binding.formatTagView.text = format
            binding.durationTagView.text = duration
            titleVisibleFilter.base.setVisible(titleValue.isNotEmpty())
            binding.dimensionsTagView.isVisible = dimensions.isNotEmpty()
            binding.sizeTagView.isVisible = size.isNotEmpty()
            binding.formatTagView.isVisible = format.isNotEmpty()
            binding.durationTagView.isVisible = duration.isNotEmpty()
        }
        log.i("updateTitle: $titleValue, $size, $format, $duration")
        if (titleValue.isEmpty()) {
            log.e("updateTitle: isEmpty", RuntimeException())
        }
    }

    private fun initInsetsListener() {
        initInsetsListener(binding.root)
        registerGuidelineInsetsListener(guidelineInsetsHelper)
        guidelineInsetsHelper.bindGuidelineInsets(
            leftGuideline = binding.startGuideLine,
            topGuideline = binding.topGuideLine,
            rightGuideline = binding.endGuideLine,
            bottomGuideline = binding.bottomGuideLine,
        )
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        videoHolder.onInsetsChanged(left, top, right, bottom)
    }

    override fun startPlaybackSpeed() {
    }

    override fun stopPlaybackSpeed() {
    }

    override fun startSeekMode() {
    }

    override fun onTouchSeek(weight: Float, precision: Float) {
    }

    override fun stopSeekMode(weight: Float) {
    }

    override fun onArchiveClick(
        position: Int,
        quick: ArchiveQuick
    ) {
    }

    override fun changeDecorationVisibility(isShow: Boolean) {
        changeDecoration(isShow)
    }
}