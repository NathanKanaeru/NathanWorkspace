package com.nathan.workspace

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.nathan.workspace.adapter.ViewPagerAdapter
import com.nathan.workspace.databinding.ActivityMainBinding

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val adapter = ViewPagerAdapter(this)
        binding.viewpager.adapter = adapter
        binding.viewpager.isUserInputEnabled = false
        binding.viewpager.offscreenPageLimit = 3

        binding.viewpager.setPageTransformer { page, position ->
            page.apply {
                val pageWidth = width
                when {
                    position < -1 -> { alpha = 0f }
                    position <= 0 -> {
                        alpha = 1f
                        translationX = 0f
                        translationZ = 0f
                        scaleX = 1f
                        scaleY = 1f
                    }
                    position <= 1 -> {
                        alpha = 1f - position
                        translationX = pageWidth * -position
                        translationZ = -1f
                        val scaleFactor = 0.75f + (1 - 0.75f) * (1 - kotlin.math.abs(position))
                        scaleX = scaleFactor
                        scaleY = scaleFactor
                    }
                    else -> { alpha = 0f }
                }
            }
        }

        binding.bottomBar.onItemSelected = { pos ->
            if (binding.viewpager.currentItem != pos) {
                binding.viewpager.currentItem = pos
            }
        }

        binding.viewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomBar.itemActiveIndex = position
            }
        })
    }
}
