package com.moshbari.yaptext.data

import com.moshbari.yaptext.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * JVZoo license verification.
 *
 * YapText Pro is sold through JVZoo (external checkout). After buying, the
 * customer receives a license key which they enter in the app. We validate
 * it against the YapText server, which is expected to confirm the key with
 * JVZoo's transaction API and return { "valid": true }.
 *
 * NOTE: the server route ([Config.LICENSE_VERIFY_PATH]) still needs to be
 * implemented. Until it exists this fails closed (returns Invalid), so only
 * the free trial works — no accidental free unlocks.
 */
class LicenseService {

    sealed interface Result {
        object Valid : Result
        data class Invalid(val message: String) : Result
    }

    suspend fun verify(licenseKey: String): Result = withContext(Dispatchers.IO) {
        val key = licenseKey.trim()
        if (key.isEmpty()) return@withContext Result.Invalid("Enter your license key")

        val payload = JSONObject().put("license", key).toString()

        val request = Request.Builder()
            .url("${Config.API_BASE_URL}${Config.LICENSE_VERIFY_PATH}")
            .addHeader("X-App-Secret", Config.APP_SECRET)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            Http.client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val valid = body
                    ?.let { runCatching { JSONObject(it).optBoolean("valid", false) }.getOrNull() }
                    ?: false

                when {
                    response.code == 200 && valid -> Result.Valid
                    response.code == 200 -> Result.Invalid("That license key wasn't recognized.")
                    response.code == 404 -> Result.Invalid("License check is not available yet. Please contact support.")
                    else -> Result.Invalid("Couldn't verify right now (${response.code}). Try again.")
                }
            }
        } catch (e: Exception) {
            Result.Invalid("Network error: ${e.localizedMessage ?: "unknown"}")
        }
    }
}
