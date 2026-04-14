package com.lollipop.mediaflow.video

import androidx.media3.common.TrackGroup

class VideoTrack(
    val group: TrackGroup,
    val index: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean
)