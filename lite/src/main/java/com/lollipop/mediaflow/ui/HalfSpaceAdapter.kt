package com.lollipop.mediaflow.ui

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.lollipop.mediaflow.view.HalfSpace

class HalfSpaceAdapter : RecyclerView.Adapter<HalfSpaceHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HalfSpaceHolder {
        return HalfSpaceHolder(parent.context)
    }

    override fun onBindViewHolder(
        holder: HalfSpaceHolder,
        position: Int
    ) {
        holder.onBind()
    }

    override fun getItemCount(): Int {
        return 1
    }

}

class HalfSpaceHolder(
    context: Context
) : RecyclerView.ViewHolder(HalfSpace(context)) {
    fun onBind() {
        itemView.post {
            val layoutParams = itemView.layoutParams
            if (layoutParams is StaggeredGridLayoutManager.LayoutParams) {
                itemView.updateLayoutParams<StaggeredGridLayoutManager.LayoutParams> {
                    isFullSpan = true // 设置为全跨列
                }
            }
        }
    }
}
