package com.nathan.workspace.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nathan.workspace.ui.ProfileFragment
import com.nathan.workspace.ui.WebViewFragment
import com.nathan.workspace.ui.WorkflowFragment

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> WorkflowFragment()
            1 -> WebViewFragment()
            2 -> ProfileFragment()
            else -> WorkflowFragment()
        }
    }
}
