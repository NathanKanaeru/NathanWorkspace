package com.nathan.workspace.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nathan.workspace.BuildConfig
import com.nathan.workspace.LoginActivity
import com.nathan.workspace.R
import com.nathan.workspace.databinding.FragmentProfileBinding
import com.nathan.workspace.viewmodel.LogEntry

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var login: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("app", Context.MODE_PRIVATE)
        login = prefs.getString("github_login", "") ?: ""
        val name = prefs.getString("github_name", "")

        binding.tvUserName.text = name?.ifBlank { login }
        binding.tvUserLogin.text = "@$login"
        binding.tvVersion.text = BuildConfig.VERSION_NAME

        loadWorkflowStats()

        binding.btnOpenGithub.setOnClickListener {
            openUrl("https://github.com/$login")
        }

        binding.btnGithubRepo.setOnClickListener {
            openUrl("https://github.com/NathanKanaeru/NathanWorkspace")
        }

        binding.btnLicenses.setOnClickListener {
            showLicensesDialog()
        }

        binding.btnClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        binding.btnClearAll.setOnClickListener {
            showClearAllDialog()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun loadWorkflowStats() {
        try {
            val workflowPrefs = requireActivity().getSharedPreferences("workflow", Context.MODE_PRIVATE)
            val json = workflowPrefs.getString("history", null)
            if (json != null) {
                val type = object : TypeToken<List<LogEntry>>() {}.type
                val history: List<LogEntry> = Gson().fromJson(json, type)

                if (history.isNotEmpty()) {
                    val total = history.size
                    val successCount = history.count { it.run.conclusion == "success" }
                    val failCount = history.count {
                        val c = it.run.conclusion
                        c != "success" && c != "cancelled" && c != "canceled"
                    }
                    val successRate = (successCount * 100) / total

                    binding.tvTotalRuns.text = total.toString()
                    binding.tvSuccessRate.text = "$successRate%"
                    binding.tvFailed.text = failCount.toString()
                    binding.tvLastRun.text = history.first().endedAt.take(10)
                }
            }
        } catch (_: Exception) { }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { }
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Workflow History")
            .setMessage("This will remove all workflow history from this device. GitHub runs will not be affected.")
            .setPositiveButton("Clear") { _, _ ->
                requireActivity().getSharedPreferences("workflow", Context.MODE_PRIVATE)
                    .edit().remove("history").apply()
                loadWorkflowStats()
                showToast("History cleared")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All Data")
            .setMessage("This will remove all workflow history and active run data from this device. GitHub data will not be affected. You will need to sign in again.")
            .setPositiveButton("Clear All") { _, _ ->
                requireActivity().getSharedPreferences("workflow", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                requireContext().getSharedPreferences("app", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                loadWorkflowStats()
                navigateToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out? Workflow history will be preserved.")
            .setPositiveButton("Sign Out") { _, _ ->
                requireContext().getSharedPreferences("app", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                navigateToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLicensesDialog() {
        val licenses = """
            Nathan Workspace
            
            Copyright 2024 NathanKanaeru
            
            Licensed under the Apache License, Version 2.0 (the "License");
            you may not use this file except in compliance with the License.
            You may obtain a copy of the License at
            
                http://www.apache.org/licenses/LICENSE-2.0
            
            Unless required by applicable law or agreed to in writing, software
            distributed under the License is distributed on an "AS IS" BASIS,
            WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            
            ---
            
            This application uses the following open source libraries:
            - OkHttp 4.12 (Apache 2.0)
            - Gson (Apache 2.0)
            - Material Components for Android (Apache 2.0)
            - Android Jetpack (Apache 2.0)
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Open Source Licenses")
            .setMessage(licenses)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finishAffinity()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
