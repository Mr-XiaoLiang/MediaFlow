package com.lollipop.mediaflow.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lollipop.mediaflow.view.HalfSpace

abstract class BasicListWearDialog : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 关键点：设置为全屏、无边框的主题
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val recyclerView = RecyclerView(inflater.context)
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        return recyclerView
    }

    protected abstract fun onViewCreated(recyclerView: RecyclerView)

    protected fun <T : RecyclerView.Adapter<*>> createAdapter(contentAdapter: T): AdapterGroup<T> {
        return AdapterGroup(
            top = SpacerAdapter(),
            content = contentAdapter,
            bottom = SpacerAdapter()
        )
    }

    protected class AdapterGroup<T : RecyclerView.Adapter<*>>(
        val top: SpacerAdapter,
        val content: T,
        val bottom: SpacerAdapter
    ) {

        val concat = ConcatAdapter(top, content, bottom)

    }

    protected class SpacerAdapter : RecyclerView.Adapter<SpacerHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            type: Int
        ): SpacerHolder {
            return SpacerHolder.create(parent.context)
        }

        override fun onBindViewHolder(
            holder: SpacerHolder,
            position: Int
        ) {
        }

        override fun getItemCount(): Int {
            return 1
        }

    }

    protected class SpacerHolder(view: View) : RecyclerView.ViewHolder(view) {

        companion object {
            fun create(context: Context): SpacerHolder {
                return SpacerHolder(
                    HalfSpace(context)
                )
            }
        }

    }

}