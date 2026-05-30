package com.lollipop.mediaflow.ui.dialog

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

abstract class BasicListSheetDialog(
    context: Context,
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recyclerView = RecyclerView(context)
        setContentView(recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        onViewCreated(recyclerView)
    }

    protected abstract fun onViewCreated(recyclerView: RecyclerView)

    protected class BottomSpacerAdapter(
        private val heightDP: Float = 60F
    ) : RecyclerView.Adapter<BottomSpacerHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            type: Int
        ): BottomSpacerHolder {
            return BottomSpacerHolder.create(parent.context, heightDP)
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

    protected class BottomSpacerHolder(view: View) : RecyclerView.ViewHolder(view) {

        companion object {
            fun create(context: Context, heightDP: Float): BottomSpacerHolder {
                return BottomSpacerHolder(
                    Space(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                heightDP,
                                context.resources.displayMetrics
                            ).toInt()
                        )
                    }
                )
            }
        }

    }

}