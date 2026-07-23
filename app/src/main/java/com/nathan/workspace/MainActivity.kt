package com.nathan.workspace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.nathan.workspace.api.GitHubApi
import com.nathan.workspace.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var token: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        token = prefs.getString("github_token", "") ?: ""

        if (token.isBlank()) {
            navigateToLogin()
            return
        }

        val login = prefs.getString("github_login", "")
        val name = prefs.getString("github_name", "")

        binding.tvUserName.text = name?.ifBlank { login }
        binding.tvUserLogin.text = "@$login"

        binding.btnRun.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            if (code.isBlank()) {
                showStatus("Masukkan CRD code terlebih dahulu")
                return@setOnClickListener
            }
            triggerWorkflow(code)
        }
    }

    private fun triggerWorkflow(code: String) {
        binding.btnRun.isEnabled = false
        binding.progressWorkflow.visibility = View.VISIBLE
        binding.cardStatus.visibility = View.VISIBLE
        binding.tvStatusDetail.text = "Mengirim trigger ke GitHub..."

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                GitHubApi.triggerWorkflow(token, code)
            }

            binding.progressWorkflow.visibility = View.GONE
            binding.btnRun.isEnabled = true

            result.fold(
                onSuccess = { msg ->
                    binding.tvStatusDetail.text = msg
                },
                onFailure = { error ->
                    binding.tvStatusDetail.text = "Gagal: ${error.message}"
                }
            )
        }
    }

    private fun showStatus(msg: String) {
        binding.cardStatus.visibility = View.VISIBLE
        binding.tvStatusDetail.text = msg
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
