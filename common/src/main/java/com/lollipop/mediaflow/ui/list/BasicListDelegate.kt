package com.lollipop.mediaflow.ui.list

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lollipop.mediaflow.data.MediaInfo

abstract class BasicListDelegate {

    abstract class BasicSpaceAdapter : RecyclerView.Adapter<SpaceHolder>() {

        private val spaceInfo: SpaceInfo = SpaceInfo(0)

        fun setSpaceDp(sizeDp: Int) {
            this.spaceInfo.setDpSize(sizeDp)
            notifyItemChanged(0)
        }

        fun setSpacePx(sizePx: Int) {
            this.spaceInfo.setPxSize(sizePx)
            notifyItemChanged(0)
        }

        abstract fun createSpaceView(context: Context): View

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpaceHolder {
            return SpaceHolder(createSpaceView(parent.context))
        }

        override fun onBindViewHolder(holder: SpaceHolder, position: Int) {
            holder.bind(spaceInfo)
        }

        override fun getItemCount(): Int {
            return 1
        }

    }

    abstract class BasicItemAdapter<VH : RecyclerView.ViewHolder>(
        val data: List<MediaInfo.File>,
    ) : RecyclerView.Adapter<VH>() {

        private var layoutInflater: LayoutInflater? = null

        protected fun getLayoutInflater(parent: ViewGroup): LayoutInflater {
            return layoutInflater ?: LayoutInflater.from(parent.context).also {
                layoutInflater = it
            }
        }

        override fun getItemCount(): Int {
            return data.size
        }

    }

    open class SpaceHolder(
        val spaceView: View
    ) : RecyclerView.ViewHolder(spaceView) {
        fun bind(spaceInfo: SpaceInfo) {
            if (spaceInfo.spaceSizePx > 0) {
                updateLayout {
                    height = spaceInfo.spaceSizePx
                    width = spaceInfo.spaceSizePx
                }
            } else if (spaceInfo.spaceSizeDp > 0) {
                updateLayout {
                    val value = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        spaceInfo.spaceSizeDp.toFloat(),
                        itemView.context.resources.displayMetrics
                    ).toInt()
                    height = value
                    width = value
                }
            }
        }

        private fun updateLayout(build: ViewGroup.LayoutParams.() -> Unit) {
            val layoutParams = spaceView.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.build()
            spaceView.layoutParams = layoutParams
        }
    }

    open class SpaceInfo(
        var spaceSizeDp: Int = 0,
        var spaceSizePx: Int = 0
    ) {

        fun setPxSize(px: Int) {
            spaceSizePx = px
            spaceSizeDp = 0
        }

        fun setDpSize(dp: Int) {
            spaceSizeDp = dp
            spaceSizePx = 0
        }

    }

    class LiningEdgeAdapter<T : RecyclerView.Adapter<*>>(
        val content: T,
        spaceAdapterProvider: () -> BasicSpaceAdapter
    ) {
        val startSpace: BasicSpaceAdapter = spaceAdapterProvider()
        val endSpace: BasicSpaceAdapter = spaceAdapterProvider()

        val root: ConcatAdapter = ConcatAdapter(
            startSpace,
            content,
            endSpace
        )
    }

}