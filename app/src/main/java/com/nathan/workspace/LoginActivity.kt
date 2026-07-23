package com.nathan.workspace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nathan.workspace.api.GitHubApi
import com.nathan.workspace.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedToken = getSharedPreferences("app", Context.MODE_PRIVATE)
            .getString("github_token", null)

        if (!savedToken.isNullOrBlank()) {
            navigateToMain()
            return
        }

        binding.etToken.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvLoginStatus.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSignin.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            if (token.isBlank()) {
                showError("Token tidak boleh kosong")
                return@setOnClickListener
            }
            authenticate(token)
        }
    }

    private fun authenticate(token: String) {
        binding.progressLogin.visibility = View.VISIBLE
        binding.btnSignin.isEnabled = false
        binding.tvLoginStatus.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                GitHubApi.validateToken(token)
            }

            binding.progressLogin.visibility = View.GONE
            binding.btnSignin.isEnabled = true

            result.fold(
                onSuccess = { user ->
                    getSharedPreferences("app", Context.MODE_PRIVATE)
                        .edit()
                        .putString("github_token", token)
                        .putString("github_login", user.login)
                        .putString("github_name", user.name)
                        .apply()
                    navigateToMain()
                },
                onFailure = { error ->
                    showError("Gagal: ${error.message}")
                }
            )
        }
    }

    private fun showError(msg: String) {
        binding.tvLoginStatus.text = msg
        binding.tvLoginStatus.visibility = View.VISIBLE
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
