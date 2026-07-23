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

    private fun authRequest(url: String, token: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")

    suspend fun validateToken(token: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$API_BASE/user", token).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                val msg = when (response.code) {
                    401 -> "Token tidak valid"
                    403 -> "Akses ditolak (rate limit?)"
                    else -> "Error ${response.code}: ${response.message}"
                }
                return@withContext Result.failure(Exception(msg))
            }
            if (body.isNullOrBlank())
                return@withContext Result.failure(Exception("Respon kosong dari server"))

            val json = try {
                JsonParser.parseString(body).asJsonObject
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Format respon tidak valid"))
            }

            Result.success(UserInfo(
                login = json.get("login")?.asString ?: "",
                name = json.get("name")?.asString ?: "",
                avatarUrl = json.get("avatar_url")?.asString ?: ""
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung: ${e.message}"))
        }
    }

    suspend fun triggerWorkflow(token: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = """{"ref":"main","inputs":{"student_code":"$code"}}""".toRequestBody(jsonMediaType)
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW/dispatches", token)
                .post(body).build().let { client.newCall(it).execute() }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Trigger failed: ${response.code}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getLatestRun(token: String): Result<WorkflowRunInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW/runs?per_page=1"
            val response = authRequest(url, token).build().let { client.newCall(it).execute() }
            val body = response.body?.string()
            if (!response.isSuccessful || body == null)
                return@withContext Result.failure(Exception("Failed to get runs: ${response.code}"))

            val json = JsonParser.parseString(body).asJsonObject
            val runs = json.getAsJsonArray("workflow_runs")
            if (runs.size() == 0) return@withContext Result.failure(Exception("No runs found"))

            val run = runs[0].asJsonObject
            Result.success(WorkflowRunInfo(
                id = run.get("id").asLong,
                status = run.get("status")?.asString ?: "unknown",
                conclusion = run.get("conclusion")?.asString,
                createdAt = run.get("created_at")?.asString ?: "",
                updatedAt = run.get("updated_at")?.asString ?: "",
                htmlUrl = run.get("html_url")?.asString ?: ""
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getRun(token: String, runId: Long): Result<WorkflowRunInfo> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId", token)
                .build().let { client.newCall(it).execute() }
            val body = response.body?.string()
            if (!response.isSuccessful || body == null)
                return@withContext Result.failure(Exception("Failed to get run: ${response.code}"))

            val json = JsonParser.parseString(body).asJsonObject
            Result.success(WorkflowRunInfo(
                id = json.get("id").asLong,
                status = json.get("status")?.asString ?: "unknown",
                conclusion = json.get("conclusion")?.asString,
                createdAt = json.get("created_at")?.asString ?: "",
                updatedAt = json.get("updated_at")?.asString ?: "",
                htmlUrl = json.get("html_url")?.asString ?: ""
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getRunLogs(token: String, runId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId/logs", token)
                .build().let { client.newCall(it).execute() }
            val body = response.body?.string()
            if (!response.isSuccessful || body == null)
                return@withContext Result.failure(Exception("Failed to get logs: ${response.code}"))
            Result.success(body)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun cancelRun(token: String, runId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId/cancel", token)
                .post("".toRequestBody(jsonMediaType)).build().let { client.newCall(it).execute() }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Cancel failed: ${response.code}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteRunLogs(token: String, runId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId/logs", token)
                .delete().build().let { client.newCall(it).execute() }
            if (response.isSuccessful || response.code == 204) Result.success(Unit)
            else Result.failure(Exception("Delete logs failed: ${response.code}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listRuns(token: String, perPage: Int = 20): Result<List<WorkflowRunInfo>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW/runs?per_page=$perPage"
            val response = authRequest(url, token).build().let { client.newCall(it).execute() }
            val body = response.body?.string()
            if (!response.isSuccessful || body == null)
                return@withContext Result.failure(Exception("Failed to list runs: ${response.code}"))

            val json = JsonParser.parseString(body).asJsonObject
            val runs = json.getAsJsonArray("workflow_runs")
            val list = runs.map { it.asJsonObject.let { obj ->
                WorkflowRunInfo(
                    id = obj.get("id").asLong,
                    status = obj.get("status")?.asString ?: "unknown",
                    conclusion = obj.get("conclusion")?.asString,
                    createdAt = obj.get("created_at")?.asString ?: "",
                    updatedAt = obj.get("updated_at")?.asString ?: "",
                    htmlUrl = obj.get("html_url")?.asString ?: ""
                )
            }}
            Result.success(list)
        } catch (e: Exception) { Result.failure(e) }
    }
}

data class UserInfo(val login: String, val name: String, val avatarUrl: String)
data class WorkflowRunInfo(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String
)
