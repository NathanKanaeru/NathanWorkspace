package com.nathan.workspace.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nathan.workspace.LoginActivity
import com.nathan.workspace.databinding.FragmentProfileBinding
import com.nathan.workspace.viewmodel.LogEntry

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("app", Context.MODE_PRIVATE)
        val login = prefs.getString("github_login", "")
        val name = prefs.getString("github_name", "")

        binding.tvUserName.text = name?.ifBlank { login }
        binding.tvUserLogin.text = "@$login"

        loadWorkflowStats()

        binding.btnLogout.setOnClickListener {
            prefs.edit().clear().apply()
            requireActivity().getSharedPreferences("workflow", Context.MODE_PRIVATE).edit().clear().apply()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finishAffinity()
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
                    val successRate = (successCount * 100) / total

                    binding.tvTotalRuns.text = total.toString()
                    binding.tvSuccessRate.text = "$successRate%"
                    binding.tvLastRun.text = history.first().endedAt.take(10)
                } else {
                    binding.tvTotalRuns.text = "0"
                    binding.tvSuccessRate.text = "-"
                    binding.tvLastRun.text = "No runs yet"
                }
            } else {
                binding.tvTotalRuns.text = "0"
                binding.tvSuccessRate.text = "-"
                binding.tvLastRun.text = "No runs yet"
            }
        } catch (_: Exception) {
            binding.tvTotalRuns.text = "-"
            binding.tvSuccessRate.text = "-"
            binding.tvLastRun.text = "-"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
