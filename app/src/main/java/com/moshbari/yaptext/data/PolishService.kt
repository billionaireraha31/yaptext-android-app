package com.moshbari.yaptext.data

import com.moshbari.yaptext.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * AI polish — Android port of PolishService.swift.
 *
 * All polish requests go through the YapText API server (/polish). Prompts
 * live ONLY on the server so iOS / Android / Mac / Chrome / Web produce
 * identical output.
 */
class PolishService {

    sealed interface Result {
        data class Success(val text: String) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun polish(text: String, tone: Tone): Result = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.Failure("Nothing to polish")

        val payload = JSONObject()
            .put("text", trimmed)
            .put("tone", tone.raw)
            .toString()

        val request = Request.Builder()
            .url("${Config.API_BASE_URL}/polish")
            .addHeader("X-App-Secret", Config.APP_SECRET)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            Http.client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                when {
                    response.code == 200 -> {
                        val parsed = body?.let { runCatching { JSONObject(it) }.getOrNull() }
                        val polished = parsed?.optString("text")?.trim()
                        if (!polished.isNullOrEmpty()) Result.Success(polished)
                        else Result.Failure("Could not parse response")
                    }
                    response.code == 401 -> Result.Failure("App auth failed — please update YapText")
                    response.code == 429 -> Result.Failure("Server busy — wait a moment")
                    else -> {
                        val serverMsg = body
                            ?.let { runCatching { JSONObject(it).optString("error") }.getOrNull() }
                            ?.takeIf { it.isNotEmpty() }
                            ?: "Polish failed (${response.code})"
                        Result.Failure(serverMsg.take(120))
                    }
                }
            }
        } catch (e: Exception) {
            Result.Failure("Network error: ${e.localizedMessage ?: "unknown"}")
        }
    }
}
