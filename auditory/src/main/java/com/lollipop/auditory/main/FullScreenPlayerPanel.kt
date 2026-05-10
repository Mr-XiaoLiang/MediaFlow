package com.lollipop.auditory.main

import android.view.LayoutInflater
import android.view.ViewGroup
import com.lollipop.auditory.databinding.FragmentPlayerFullBinding
import com.lollipop.auditory.databinding.ItemPlaySongBinding
import com.lollipop.auditory.ui.view.CoverViewPagerAdapter

class FullScreenPlayerPanel(
    val binding: FragmentPlayerFullBinding
) {

    fun onCreate() {
        binding.coverViewPager.adapter = object : CoverViewPagerAdapter<CoverHolder>() {

            private val layoutInflater = LayoutInflater.from(binding.coverViewPager.context)

            override fun getCount(): Int {
                return 20
            }

            override fun createViewHolder(container: ViewGroup): CoverHolder {
                return CoverHolder(ItemPlaySongBinding.inflate(layoutInflater))
            }

            override fun bindViewHolder(
                holder: CoverHolder,
                position: Int
            ) {
                holder.binding.titleTextView.text = "Holder $position"
            }

        }
    }

    class CoverHolder(val binding: ItemPlaySongBinding) :
        CoverViewPagerAdapter.ViewHolder(binding.root) {

    }

    fun onExpand() {

    }

    fun onCollapse() {

    }

    fun onResume() {

    }

    fun onPause() {

    }

}