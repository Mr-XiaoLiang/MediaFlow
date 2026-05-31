package com.lollipop.mediaflow.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import com.lollipop.common.ui.page.CustomOrientationActivity
import com.lollipop.mediaflow.databinding.ActivityFlowBinding

abstract class BasicFlowActivity : CustomOrientationActivity() {

    private val basicBinding by lazy {
        ActivityFlowBinding.inflate(layoutInflater)
    }

    protected var isDecorationShown = true
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(basicBinding.root)
        initInsetsListener()
        basicBinding.contentContainer.addView(
            createContentPanel(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    protected fun changeDecoration(isVisibility: Boolean) {
        isDecorationShown = isVisibility
    }

    private fun initInsetsListener() {
        initInsetsListener(basicBinding.root)
    }

    protected abstract fun createContentPanel(): View

}