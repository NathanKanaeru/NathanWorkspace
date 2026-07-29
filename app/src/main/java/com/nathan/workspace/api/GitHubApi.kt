package com.nathan.workspace.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
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
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun JsonElement?.safeString(): String {
        if (this == null || isJsonNull) return ""
        return asString
    }

    private fun authRequest(url: String, token: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "NathanWorkspace-Android/1.4")

    private fun parseJson(body: String): Result<JsonObject> {
        return try {
            val parsed = JsonParser.parseString(body)
            if (!parsed.isJsonObject)
                Result.failure(Exception("Respon bukan JSON object: ${body.take(200)}"))
            else
                Result.success(parsed.asJsonObject)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal parse JSON: ${body.take(200)}"))
        }
    }

    private fun parseWorkflowRun(obj: JsonObject) = WorkflowRunInfo(
        id = obj.get("id").asLong,
        status = obj.get("status").safeString().ifBlank { "unknown" },
        conclusion = obj.get("conclusion").safeString().ifBlank { null },
        createdAt = obj.get("created_at").safeString(),
        updatedAt = obj.get("updated_at").safeString(),
        htmlUrl = obj.get("html_url").safeString()
    )

    suspend fun validateToken(token: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$API_BASE/user", token).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            val code = response.code

            if (!response.isSuccessful) {
                val snippet = if (!body.isNullOrBlank()) body.take(200) else ""
                val msg = when (code) {
                    401 -> "Token tidak valid"
                    403 -> "Akses ditolak"
                    else -> "Error $code${if (snippet.isNotBlank()) ": $snippet" else ""}"
                }
                return@withContext Result.failure(Exception(msg))
            }
            if (body.isNullOrBlank())
                return@withContext Result.failure(Exception("Respon kosong (200 OK, body kosong)"))

            val parsed = JsonParser.parseString(body)
            if (!parsed.isJsonObject) {
                return@withContext Result.failure(
                    Exception("Respon bukan JSON object: ${body.take(200)}")
                )
            }
            val json = parsed.asJsonObject

            Result.success(UserInfo(
                login = json.get("login").safeString(),
                name = json.get("name").safeString(),
                avatarUrl = json.get("avatar_url").safeString()
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung: ${e.message}"))
        }
    }

    suspend fun triggerWorkflow(token: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val escapedCode = code.replace("\\", "\\\\").replace("\"", "\\\"")
            val body = """{"ref":"main","inputs":{"student_code":"$escapedCode"}}""".toRequestBody(jsonMediaType)
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
            if (!response.isSuccessful || body.isNullOrBlank())
                return@withContext Result.failure(Exception("Gagal ambil runs (${response.code})"))

            val json = parseJson(body).getOrElse { return@withContext Result.failure(it) }
            val runs = json.getAsJsonArray("workflow_runs") ?: return@withContext Result.failure(Exception("Tidak ada field workflow_runs"))
            if (runs.size() == 0) return@withContext Result.failure(Exception("Belum ada runs"))

            Result.success(parseWorkflowRun(runs[0].asJsonObject))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getRun(token: String, runId: Long): Result<WorkflowRunInfo> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId", token)
                .build().let { client.newCall(it).execute() }
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank())
                return@withContext Result.failure(Exception("Gagal ambil run (${response.code})"))

            val json = parseJson(body).getOrElse { return@withContext Result.failure(it) }
            Result.success(parseWorkflowRun(json))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getRunLogs(token: String, runId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId/logs", token)
                .build().let { client.newCall(it).execute() }
            val body = response.body?.string()
            if (!response.isSuccessful || body == null)
                return@withContext Result.failure(Exception("Gagal ambil logs (${response.code})"))
            Result.success(body)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun cancelRun(token: String, runId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId/cancel", token)
                .post("".toRequestBody(jsonMediaType)).build().let { client.newCall(it).execute() }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Gagal cancel (${response.code})"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteRunLogs(token: String, runId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId/logs", token)
                .delete().build().let { client.newCall(it).execute() }
            if (response.isSuccessful || response.code == 204) Result.success(Unit)
            else Result.failure(Exception("Gagal hapus logs (${response.code})"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getRunJobs(token: String, runId: Long): Result<List<JobInfo>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId/jobs"
            val response = authRequest(url, token).build().let { client.newCall(it).execute() }
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank())
                return@withContext Result.failure(Exception("Gagal ambil jobs (${response.code})"))

            val json = parseJson(body).getOrElse { return@withContext Result.failure(it) }
            val arr = json.getAsJsonArray("jobs") ?: return@withContext Result.failure(Exception("Tidak ada field jobs"))
            val jobs = arr.map { it.asJsonObject }.map { obj ->
                val stepsArr = obj.getAsJsonArray("steps")
                val steps = stepsArr?.map { it.asJsonObject }?.map { step ->
                    StepInfo(
                        name = step.get("name").safeString(),
                        status = step.get("status").safeString(),
                        conclusion = step.get("conclusion").safeString().ifBlank { null },
                        number = step.get("number").asInt
                    )
                } ?: emptyList()

                JobInfo(
                    id = obj.get("id").asLong,
                    name = obj.get("name").safeString(),
                    status = obj.get("status").safeString(),
                    conclusion = obj.get("conclusion").safeString().ifBlank { null },
                    startedAt = obj.get("started_at").safeString(),
                    completedAt = obj.get("completed_at").safeString(),
                    steps = steps
                )
            }
            Result.success(jobs)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getJobLogs(token: String, jobId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/jobs/$jobId/logs", token)
                .build().let { client.newCall(it).execute() }
            val body = response.body?.string()
            if (!response.isSuccessful || body == null)
                return@withContext Result.failure(Exception("Gagal ambil job logs (${response.code})"))
            Result.success(body)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteRun(token: String, runId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = authRequest("$API_BASE/repos/$OWNER/$REPO/actions/runs/$runId", token)
                .delete().build().let { client.newCall(it).execute() }
            if (response.isSuccessful || response.code == 204) Result.success(Unit)
            else Result.failure(Exception("Gagal hapus run (${response.code})"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listRuns(token: String, perPage: Int = 20): Result<List<WorkflowRunInfo>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW/runs?per_page=$perPage"
            val response = authRequest(url, token).build().let { client.newCall(it).execute() }
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank())
                return@withContext Result.failure(Exception("Gagal list runs (${response.code})"))

            val json = parseJson(body).getOrElse { return@withContext Result.failure(it) }
            val arr = json.getAsJsonArray("workflow_runs") ?: return@withContext Result.failure(Exception("Tidak ada field workflow_runs"))
            Result.success(arr.map { parseWorkflowRun(it.asJsonObject) })
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
data class JobInfo(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: String,
    val completedAt: String,
    val steps: List<StepInfo>
)
data class StepInfo(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int
)
