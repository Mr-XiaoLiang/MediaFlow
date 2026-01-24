package com.lollipop.mediaflow.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.databinding.ItemMediaGridBinding

abstract class MediaGridFragment : InsetsFragment() {

    protected open class MediaItemAdapter(
        val data: List<MediaInfo.File>,
        val onItemClick: (MediaInfo.File) -> Unit
    ) : RecyclerView.Adapter<MediaItemHolder>() {

        private var layoutInflater: LayoutInflater? = null

        protected fun getLayoutInflater(parent: ViewGroup): LayoutInflater {
            return layoutInflater ?: LayoutInflater.from(parent.context).also {
                layoutInflater = it
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemHolder {
            return MediaItemHolder(
                ItemMediaGridBinding.inflate(getLayoutInflater(parent), parent, false),
                ::onHolderClick
            )
        }

        private fun onHolderClick(position: Int) {
            if (position >= 0 && position < data.size) {
                onItemClick(data[position])
            }
        }

        override fun onBindViewHolder(holder: MediaItemHolder, position: Int) {
            holder.bind(data[position])
        }

        override fun getItemCount(): Int {
            return data.size
        }

    }

    protected open class MediaItemHolder(
        val binding: ItemMediaGridBinding,
        val onClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                onClick(bindingAdapterPosition)
            }
        }

        fun bind(mediaInfo: MediaInfo.File) {
            Glide.with(itemView)
                .load(mediaInfo.uri)
                .into(binding.mediaPreview)
            val duration = mediaInfo.metadata?.duration ?: 0
            if (duration > 0) {
                binding.durationView.isVisible = true
                binding.durationView.text = mediaInfo.metadata?.durationFormat ?: ""
            } else {
                binding.durationView.isVisible = false
            }
        }

    }

}