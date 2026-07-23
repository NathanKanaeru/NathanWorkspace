package com.nathan.workspace.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.nathan.workspace.LoginActivity
import com.nathan.workspace.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
