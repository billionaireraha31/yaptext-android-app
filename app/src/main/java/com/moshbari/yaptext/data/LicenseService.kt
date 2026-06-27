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
 * YapText Pro is sold through JVZoo on the web app. On purchase, the JVZoo
 * webhook (Supabase function `jvzoo-delivery`) generates a unique license key
 * and shows/emails it to the buyer. The buyer types that key into the Android
 * app; this service checks it against the Supabase `verify-license` function,
 * which confirms the key exists and is active (not refunded).
 */
class LicenseService {

    sealed interface Result {
        object Valid : Result
        data class Invalid(val message: String) : Result
        /** Network/unknown failure — caller may fall back to the offline code. */
        data class Unreachable(val message: String) : Result
    }

    suspend fun verify(licenseKey: String): Result = withContext(Dispatchers.IO) {
        val key = licenseKey.trim()
        if (key.isEmpty()) return@withContext Result.Invalid("Enter your license key")

        val payload = JSONObject().put("license", key).toString()

        val request = Request.Builder()
            .url(Config.LICENSE_VERIFY_URL)
            .addHeader("apikey", Config.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${Config.SUPABASE_ANON_KEY}")
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
                    else -> Result.Unreachable("Couldn't verify right now (${response.code}).")
                }
            }
        } catch (e: Exception) {
            Result.Unreachable("Network error: ${e.localizedMessage ?: "unknown"}")
        }
    }
}
