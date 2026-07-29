package com.nathan.workspace

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.nathan.workspace.adapter.ViewPagerAdapter
import com.nathan.workspace.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = ViewPagerAdapter(this)
        binding.viewpager.adapter = adapter
        binding.viewpager.isUserInputEnabled = false
        binding.viewpager.offscreenPageLimit = 3

        binding.viewpager.setPageTransformer { page, position ->
            val absPos = kotlin.math.abs(position)
            page.alpha = 1 - absPos * 0.3f
            page.translationX = position * page.width * 0.1f
            page.scaleY = 1 - absPos * 0.05f
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
