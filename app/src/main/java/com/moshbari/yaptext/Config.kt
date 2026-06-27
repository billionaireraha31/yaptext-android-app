package com.moshbari.yaptext

/**
 * Central configuration — Android port of YapTextConfig (Config.swift).
 *
 * All backend URLs, secrets, and product identifiers live here.
 * Single source of truth for the app, the keyboard (IME) and the widget.
 *
 * The backend is the SAME Railway server the iOS / Mac / Chrome / Web
 * clients use, so transcription and polish output stay identical across
 * platforms. OpenAI / Sarvam keys live ONLY on the server.
 */
object Config {

    // MARK: - Backend (Railway)

    /** Base URL of the YapText API server. */
    const val API_BASE_URL = "https://yaptext-api-production.up.railway.app"

    /**
     * App secret sent in the X-App-Secret header on every API request.
     * The server rejects requests without it.
     *
     * ⚠️ Can be extracted from the APK by a determined attacker — it raises
     * the bar against casual abuse but is not a hard secret. Server-side
     * rate limiting + abuse detection is the real protection layer.
     */
    const val APP_SECRET =
        "6cf0dbc29b678a29f462c945b1f09c15fc02ae03d07d626071912fc1c09e7e61"

    // MARK: - Payments (JVZoo)

    /**
     * JVZoo hosted checkout URL for YapText Pro. Tapping "Upgrade" opens this
     * in the browser. After purchase the buyer receives a license key which
     * they enter in the app; the app verifies it against the server.
     */
    // TODO(you): paste your real JVZoo checkout link here after you create
    // the "YapText Pro" product in your JVZoo seller dashboard.
    const val JVZOO_CHECKOUT_URL = "https://www.jvzoo.com/b/0/000000/2"

    // MARK: - License verification (Supabase — shared with the web app)

    /**
     * Supabase project that issues + verifies JVZoo license keys. Same project
     * the web app uses. The anon key is a publishable key (safe to embed — the
     * web app already ships it to every browser).
     */
    const val SUPABASE_URL = "https://cgogrstoqhmzkuxvcakk.supabase.co"
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNnb2dyc3RvcWhtemt1eHZjYWtrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjQxMjIwNTgsImV4cCI6MjA3OTY5ODA1OH0.JFKWm9SJarBlPia0oNO5ZG-lfb9y4OlVu2CIB0mnfvg"

    /** Edge function the app calls to check a license key. */
    const val LICENSE_VERIFY_URL = "$SUPABASE_URL/functions/v1/verify-license"

    /**
     * Offline fallback unlock code. Used only if the server can't be reached
     * (e.g. before the edge functions are deployed). Set to "" to disable once
     * real JVZoo license verification is live.
     */
    const val PRO_UNLOCK_CODE_FALLBACK = "YAPTEXT-PRO-2026"

    // MARK: - Recording limits

    /** Maximum recording length per session (seconds). Auto-stops at the cap. */
    const val MAX_RECORDING_SECONDS = 300.0          // 5 minutes

    /** Show a warning when within this many seconds of the cap. */
    const val RECORDING_WARNING_SECONDS = 30.0

    /** Auto-stop after this many seconds of continuous silence. */
    const val SILENCE_TIMEOUT_SECONDS = 30.0

    /**
     * MediaRecorder.maxAmplitude is a linear 0..32767 value (not dB like iOS).
     * Below this we treat the input as silence. ~1500 ≈ quiet room.
     */
    const val SILENCE_AMPLITUDE_THRESHOLD = 1500

    // MARK: - Free trial usage

    /**
     * Total seconds of transcription allowed for non-subscribers before the
     * paywall hard-gates them. Defends against trial farming.
     */
    const val FREE_TRIAL_TOTAL_SECONDS = 1800.0      // 30 minutes
}
