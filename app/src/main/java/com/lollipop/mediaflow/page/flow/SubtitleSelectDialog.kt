package com.lollipop.mediaflow.page.flow

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.databinding.ItemDialogSubtitleSelectBinding
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.video.VideoTrack
import com.lollipop.mediaflow.video.VideoTrackGroup

class SubtitleSelectDialog(
    context: Context,
    val subtitleList: VideoTrackGroup,
    val onSelect: (VideoTrack?) -> Unit
) : BottomSheetDialog(context) {

    private val log = registerLog()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recyclerView = RecyclerView(context)
        setContentView(recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        val dataList = mutableListOf<ItemInfo>()
        if (subtitleList.enable) {
            dataList.add(ItemInfo.CloseUnselected)
        } else {
            dataList.add(ItemInfo.CloseSelected)
        }
        subtitleList.tracks.forEach {
            log.i("onCreate.subtitle = ${it.label}")
            dataList.add(ItemInfo.Subtitle(it))
        }
        val itemAdapter = ItemAdapter(dataList, ::onItemSelect)
        recyclerView.adapter = ConcatAdapter(itemAdapter, BottomSpacerAdapter())
    }

    private fun onItemSelect(track: ItemInfo) {
        onSelect(track.trackInfo())
        dismiss()
    }

    private class ItemAdapter(
        val subtitleList: List<ItemInfo>,
        val onSelect: (ItemInfo) -> Unit
    ) : RecyclerView.Adapter<ItemHolder>() {

        private var layoutInflater: LayoutInflater? = null

        private fun getLayoutInflater(parent: ViewGroup): LayoutInflater {
            return layoutInflater ?: LayoutInflater.from(parent.context).also {
                layoutInflater = it
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ItemHolder {
            return ItemHolder(
                binding = ItemDialogSubtitleSelectBinding.inflate(
                    getLayoutInflater(parent),
                    parent,
                    false
                ),
                onSelect = ::onSelect
            )
        }

        override fun onBindViewHolder(
            holder: ItemHolder,
            position: Int
        ) {
            val subtitle = subtitleList[position]
            holder.onBind(subtitle)
        }

        override fun getItemCount(): Int {
            return subtitleList.size
        }

        private fun onSelect(position: Int) {
            if (position < 0 || position >= subtitleList.size) {
                return
            }
            onSelect(subtitleList[position])
        }

    }

    private class BottomSpacerAdapter : RecyclerView.Adapter<BottomSpacerHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            type: Int
        ): BottomSpacerHolder {
            return BottomSpacerHolder.create(parent.context)
        }

        override fun onBindViewHolder(
            holder: BottomSpacerHolder,
            position: Int
        ) {
        }

        override fun getItemCount(): Int {
            return 1
        }

    }

    private class BottomSpacerHolder(view: View) : RecyclerView.ViewHolder(view) {

        companion object {
            fun create(context: Context): BottomSpacerHolder {
                return BottomSpacerHolder(
                    Space(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                60F,
                                context.resources.displayMetrics
                            ).toInt()
                        )
                    }
                )
            }
        }

    }

    private class ItemHolder(
        val binding: ItemDialogSubtitleSelectBinding,
        val onSelect: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener { onClick() }
        }

        private fun onClick() {
            onSelect(bindingAdapterPosition)
        }

        fun onBind(info: ItemInfo) {
            binding.labelView.text = info.label(itemView.context)
            val language = info.language(itemView.context)
            binding.languageView.text = language
            binding.languageView.isVisible = language.isNotEmpty()
            binding.selectedFlagView.isInvisible = !info.isSelected()
        }

    }

    private sealed class ItemInfo {

        abstract fun label(context: Context): String

        abstract fun language(context: Context): String

        abstract fun isSelected(): Boolean

        abstract fun trackInfo(): VideoTrack?

        object CloseUnselected : ItemInfo() {
            override fun label(context: Context): String {
                return context.getString(R.string.label_close_subtitle_on_play)
            }

            override fun language(context: Context): String {
                return ""
            }

            override fun isSelected(): Boolean {
                return false
            }

            override fun trackInfo(): VideoTrack? {
                return null
            }

        }

        object CloseSelected : ItemInfo() {
            override fun label(context: Context): String {
                return context.getString(R.string.label_close_subtitle_on_play)
            }

            override fun language(context: Context): String {
                return ""
            }

            override fun isSelected(): Boolean {
                return true
            }

            override fun trackInfo(): VideoTrack? {
                return null
            }
        }

        class Subtitle(val subtitle: VideoTrack) : ItemInfo() {
            override fun label(context: Context): String {
                return subtitle.label
            }

            override fun language(context: Context): String {
                return subtitle.language
            }

            override fun isSelected(): Boolean {
                return subtitle.isSelected
            }

            override fun trackInfo(): VideoTrack {
                return subtitle
            }
        }

    }

}