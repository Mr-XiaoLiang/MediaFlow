package com.lollipop.mediaflow

import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.lollipop.mediaflow.databinding.ActivityMainBinding
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.InsetsFragment

class MainActivity : AppCompatActivity(), InsetsFragment.Provider {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val insetsProviderHelper = InsetsFragment.ProviderHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        initInsetsListener()
        binding.tabGroup.select(0)
        binding.publicVideoTab.setOnClickListener {
            selectTab(0)
        }
        binding.publicPhotoTab.setOnClickListener {
            selectTab(1)
        }
        binding.privateVideoTab.setOnClickListener {
            selectTab(2)
        }
        binding.privatePhotoTab.setOnClickListener {
            selectTab(3)
        }
        binding.viewPager2.also {
            it.adapter = SubPageAdapter(this)
            it.isUserInputEnabled = false
        }
        binding.privateVideoTab.isVisible = true
        binding.privatePhotoTab.isVisible = true
    }

    private fun selectTab(index: Int) {
        binding.tabGroup.select(index)
        binding.viewPager2.setCurrentItem(index, false)
    }

    private fun initInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            onWindowInsetsChanged(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
        binding.tabBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            insetsProviderHelper.updateInsets(
                bottom = binding.viewPager2.bottom - binding.tabBar.top
            )
        }
    }

    private fun onWindowInsetsChanged(left: Int, top: Int, right: Int, bottom: Int) {
        val minEdge = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            16f,
            resources.displayMetrics
        ).toInt()
        binding.startGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
            guideBegin = maxOf(left, minEdge)
        }
        binding.topGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
            guideBegin = maxOf(top, minEdge)
        }
        binding.endGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
            guideEnd = maxOf(right, minEdge)
        }
        binding.bottomGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
            guideEnd = maxOf(bottom, minEdge)
        }
        insetsProviderHelper.updateInsets(left = left, top = top, right = right)
    }

    override fun getInsets(): Rect {
        return insetsProviderHelper.getInsets()
    }

    override fun registerInsetsListener(listener: InsetsFragment.InsetsListener) {
        insetsProviderHelper.registerInsetsListener(listener)
    }

    override fun unregisterInsetsListener(listener: InsetsFragment.InsetsListener) {
        insetsProviderHelper.unregisterInsetsListener(listener)
    }

    private class SubPageAdapter(
        fragmentActivity: FragmentActivity
    ) : FragmentStateAdapter(fragmentActivity) {

        private val pageArray = HomePage.entries

        override fun createFragment(position: Int): Fragment {
            return pageArray[position].pageClass.getDeclaredConstructor().newInstance()
        }

        override fun getItemCount(): Int {
            return pageArray.size
        }

    }

}