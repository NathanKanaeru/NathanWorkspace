package com.nathan.workspace.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nathan.workspace.BuildConfig
import com.nathan.workspace.LoginActivity
import com.nathan.workspace.databinding.FragmentProfileBinding
import com.nathan.workspace.viewmodel.WorkflowViewModel
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var login: String = ""
    private val viewModel: WorkflowViewModel by activityViewModels()

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.runs.collect { runs ->
                    if (runs.isNotEmpty()) {
                        val total = runs.size
                        val successCount = runs.count { it.conclusion == "success" }
                        val failCount = runs.count {
                            val c = it.conclusion
                            c != null && c != "success" && c != "cancelled" && c != "canceled"
                        }
                        val successRate = if (total > 0) (successCount * 100) / total else 0

                        binding.tvTotalRuns.text = total.toString()
                        binding.tvSuccessRate.text = "$successRate%"
                        binding.tvFailed.text = failCount.toString()
                        binding.tvLastRun.text = runs.first().createdAt.take(10)
                    } else {
                        binding.tvTotalRuns.text = "0"
                        binding.tvSuccessRate.text = "0%"
                        binding.tvFailed.text = "0"
                        binding.tvLastRun.text = "-"
                    }
                }
            }
        }

        binding.btnOpenGithub.setOnClickListener {
            openUrl("https://github.com/$login")
        }

        binding.btnGithubRepo.setOnClickListener {
            openUrl("https://github.com/NathanKanaeru/NathanWorkspace")
        }

        binding.btnLicenses.setOnClickListener {
            showLicensesDialog()
        }

        binding.btnClearCache.setOnClickListener {
            showClearCacheDialog()
        }

        binding.btnClearAll.setOnClickListener {
            showClearAllDialog()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { }
    }

    private fun showClearCacheDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Download Cache")
            .setMessage("This will remove all downloaded APK files from your device. Do you want to continue?")
            .setPositiveButton("Clear") { _, _ ->
                val dir = java.io.File(requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "")
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { it.delete() }
                }
                android.widget.Toast.makeText(requireContext(), "Download cache cleared", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearAllDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Local Data")
            .setMessage("This will remove local app data. GitHub data will not be affected. You will need to sign in again.")
            .setPositiveButton("Clear Data") { _, _ ->
                requireActivity().getSharedPreferences("workflow", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                requireContext().getSharedPreferences("app", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                navigateToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLogoutDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                requireContext().getSharedPreferences("app", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                navigateToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLicensesDialog() {
        val licenses = android.text.Html.fromHtml(
            """
            <b>Nathan Workspace</b><br><br>
            Copyright 2026 NathanKanaeru<br><br>
            Licensed under the Apache License, Version 2.0 (the "License");<br>
            you may not use this file except in compliance with the License.<br>
            You may obtain a copy of the License at<br><br>
            &nbsp;&nbsp;&nbsp;&nbsp;http://www.apache.org/licenses/LICENSE-2.0<br><br>
            Unless required by applicable law or agreed to in writing, software<br>
            distributed under the License is distributed on an "AS IS" BASIS,<br>
            WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.<br>
            <br><hr><br>
            <b>Third-Party Libraries:</b><br>
            • OkHttp 4.12 <i>(Apache 2.0)</i><br>
            • Gson <i>(Apache 2.0)</i><br>
            • Material Components for Android <i>(Apache 2.0)</i><br>
            • Android Jetpack <i>(Apache 2.0)</i>
            """.trimIndent(),
            android.text.Html.FROM_HTML_MODE_COMPACT
        )

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
