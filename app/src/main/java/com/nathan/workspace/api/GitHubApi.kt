package com.nathan.workspace.api

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GitHubApi {
    private const val API_BASE = "https://api.github.com"
    private const val OWNER = "BagasZkyn"
    private const val REPO = "studentcolab"
    private const val WORKFLOW = "student.yml"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun validateToken(token: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_BASE/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(Exception("Token invalid: ${response.code}"))
            }

            val json = JsonParser.parseString(body).asJsonObject
            val login = json.get("login")?.asString ?: ""
            val name = json.get("name")?.asString ?: login
            val avatar = json.get("avatar_url")?.asString ?: ""

            Result.success(UserInfo(login, name, avatar))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerWorkflow(token: String, code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = """{"ref":"main","inputs":{"student_code":"$code"}}"""
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$API_BASE/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW/dispatches")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .post(jsonBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success("Workflow triggered successfully!")
            } else {
                val errorBody = response.body?.string()
                Result.failure(Exception("Failed: ${response.code} $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class UserInfo(
    val login: String,
    val name: String,
    val avatarUrl: String
)
