package com.lollipop.mediaflow.tools

import android.content.Context
import androidx.fragment.app.Fragment

inline fun <reified T : Any> Fragment.fetchCallback(ctx: Context): T? {
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