package com.lollipop.mediaflow

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

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
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
    }

}