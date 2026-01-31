package com.lollipop.mediaflow.page

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.databinding.PageVideoFlowBinding
import com.lollipop.mediaflow.ui.BasicFlowActivity

class VideoFlowActivity : BasicFlowActivity() {


    override fun onOrientationChanged(orientation: Orientation) {
//        TODO("Not yet implemented")
    }

    override fun onDrawerChanged(isOpen: Boolean) {
//        TODO("Not yet implemented")
    }

    override fun createDrawerPanel(): View {
        TODO("Not yet implemented")
    }

    override fun buildContentPanel(viewPager2: ViewPager2) {
//        TODO("Not yet implemented")
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
//        TODO("Not yet implemented")
    }

    private class VideoHolder(
        private val binding: PageVideoFlowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val clickHelper = ClickHelper(onClick = ::onClick)

        init {
            binding.root.setOnClickListener(clickHelper)
        }

        fun onInsetsChanged(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int
        ) {
            binding.controlLayout.setPadding(left, top, right, bottom)
        }

        fun onBind(media: MediaInfo.File) {
            clickHelper.reset()
            Glide.with(itemView)
                .load(media.uri)
                .into(binding.artworkView)
            // TODO
        }

        private fun onClick(clickCount: Int) {
            if (clickCount == 1) {
                // 点击一次
            } else if (clickCount == 2) {
                // 点击两次
            }
        }

    }

    private class ClickHelper(
        private val keepTimeMs: Long = 300,
        private val onClick: (Int) -> Unit
    ) : View.OnClickListener {
        private var lastClickTime: Long = 0
        private var clickCount = 0

        fun reset() {
            clickCount = 0
            lastClickTime = 0
        }

        override fun onClick(v: View?) {
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastClickTime) < keepTimeMs) {
                clickCount++
            } else {
                clickCount = 1
            }
            lastClickTime = currentTime
            onClick.invoke(clickCount)
        }

    }

}