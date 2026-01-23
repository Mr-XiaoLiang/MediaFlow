package com.lollipop.mediaflow.ui

import android.graphics.Rect
import androidx.fragment.app.Fragment

abstract class InsetsFragment: Fragment() {

    interface Provider {

        fun getInsets(): Rect

    }

}