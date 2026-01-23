package com.lollipop.mediaflow.ui

import android.content.Context
import android.graphics.Rect
import androidx.fragment.app.Fragment

abstract class InsetsFragment : Fragment() {

    private var insetsProvider: Provider? = null

    private val insetsListener = object : InsetsListener {
        override fun onInsetsChanged(insets: Rect) {
            onWindowInsetsChanged(insets)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        insetsProvider = fetchCallback(context)
        insetsProvider?.registerInsetsListener(insetsListener)
    }

    override fun onDetach() {
        super.onDetach()
        insetsProvider?.unregisterInsetsListener(insetsListener)
        insetsProvider = null
    }

    protected fun requestInsets() {
        insetsProvider?.let {
            onWindowInsetsChanged(it.getInsets())
        }
    }

    protected open fun onWindowInsetsChanged(insets: Rect) {

    }

    protected inline fun <reified T : Any> fetchCallback(ctx: Context): T? {
        var parent: Fragment? = parentFragment
        while (parent != null) {
            if (parent is T) {
                return parent
            }
            parent = parent.parentFragment
        }
        if (ctx is T) {
            return ctx
        }
        activity?.let {
            if (it is T) {
                return it
            }
        }
        context?.let {
            if (it is T) {
                return it
            }
        }
        return null
    }

    interface Provider {

        fun getInsets(): Rect

        fun registerInsetsListener(listener: InsetsListener)

        fun unregisterInsetsListener(listener: InsetsListener)

    }

    interface InsetsListener {

        fun onInsetsChanged(insets: Rect)

    }

    class ProviderHelper : Provider {

        private val insets = Rect()

        private val outTemp = Rect()

        private val listeners = mutableListOf<InsetsListener>()

        override fun getInsets(): Rect {
            outTemp.set(insets)
            return outTemp
        }

        override fun registerInsetsListener(listener: InsetsListener) {
            listeners.add(listener)
            outTemp.set(insets)
            listener.onInsetsChanged(outTemp)
        }

        override fun unregisterInsetsListener(listener: InsetsListener) {
            listeners.remove(listener)
        }

        fun updateInsets(
            left: Int = insets.left,
            top: Int = insets.top,
            right: Int = insets.right,
            bottom: Int = insets.bottom
        ) {
            if (insets.left == left && insets.top == top && insets.right == right && insets.bottom == bottom) {
                return
            }
            setInsets(left, top, right, bottom)
        }

        fun setInsets(left: Int, top: Int, right: Int, bottom: Int) {
            this.insets.set(left, top, right, bottom)
            listeners.forEach {
                outTemp.set(left, top, right, bottom)
                it.onInsetsChanged(outTemp)
            }
        }

    }

}