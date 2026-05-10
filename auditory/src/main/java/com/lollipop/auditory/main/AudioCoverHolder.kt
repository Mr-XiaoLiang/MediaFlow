package com.lollipop.auditory.main

import android.annotation.SuppressLint
import com.lollipop.auditory.data.AudioInfo
import com.lollipop.auditory.databinding.ItemPlaySongBinding
import com.lollipop.auditory.ui.view.CoverViewPagerAdapter

class AudioCoverHolder(
    private val binding: ItemPlaySongBinding
) : CoverViewPagerAdapter.ViewHolder(binding.root) {

    @SuppressLint("SetTextI18n")
    fun bind(audioInfo: AudioInfo, allCount: Int, position: Int) {
        binding.titleTextView.text = audioInfo.title
        binding.pageNumberView.text = "${position + 1}/$allCount"
    }

}