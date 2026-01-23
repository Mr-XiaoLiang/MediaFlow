package com.lollipop.mediaflow

import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.lollipop.mediaflow.databinding.ActivityMainBinding
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
            binding.tabGroup.select(0)
        }
        binding.publicPhotoTab.setOnClickListener {
            binding.tabGroup.select(1)
        }
        binding.privateVideoTab.setOnClickListener {
            binding.tabGroup.select(2)
        }
        binding.privatePhotoTab.setOnClickListener {
            binding.tabGroup.select(3)
        }
        binding.privateVideoTab.isVisible = false
        binding.privatePhotoTab.isVisible = false
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
        binding.tabBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            val dp20 = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20F,
                resources.displayMetrics
            )
            bottomMargin = (dp20 + bottom).toInt()
            leftMargin = left
            rightMargin = right
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

}