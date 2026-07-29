package com.nathan.workspace.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nathan.workspace.ui.ProfileFragment
import com.nathan.workspace.ui.RepoFragment
import com.nathan.workspace.ui.WebViewFragment
import com.nathan.workspace.ui.WorkflowFragment

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> WorkflowFragment()
            1 -> RepoFragment()
            2 -> WebViewFragment()
            3 -> ProfileFragment()
            else -> WorkflowFragment()
        }
    }
}
