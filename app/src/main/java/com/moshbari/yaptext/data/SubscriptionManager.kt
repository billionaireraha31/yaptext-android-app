package com.moshbari.yaptext.data

import com.moshbari.yaptext.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Subscription / trial state — Android port of SubscriptionManager.swift.
 *
 * iOS used RevenueCat; on Android, Pro is sold via JVZoo (external checkout)
 * and unlocked with a license key (see [LicenseService]). This object is the
 * single source of truth for "is this user Pro?" and the free-trial gate.
 *
 * Pro status + trial usage are persisted in [AppStorage], so the keyboard
 * (IME) and widget can read them too.
 */
object SubscriptionManager {

    private val licenseService = LicenseService()

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** Bump this to force Compose recomposition after trial seconds change. */
    private val _trialTick = MutableStateFlow(0)
    val trialTick: StateFlow<Int> = _trialTick.asStateFlow()

    /** Re-read persisted state. Safe to call any time (e.g. on resume). */
    fun refresh() {
        _isPro.value = AppStorage.isPro
        _trialTick.value = _trialTick.value + 1
    }

    fun noteTrialChanged() {
        _trialTick.value = _trialTick.value + 1
    }

    // MARK: - Trial gating

    val trialExhausted: Boolean
        get() = AppStorage.trialSecondsUsed >= Config.FREE_TRIAL_TOTAL_SECONDS

    val trialSecondsRemaining: Double
        get() = (Config.FREE_TRIAL_TOTAL_SECONDS - AppStorage.trialSecondsUsed).coerceAtLeast(0.0)

    /** Pretty string like "12 min 3s left in trial" or "Trial used up". */
    val trialRemainingText: String
        get() {
            val remaining = trialSecondsRemaining
            if (remaining <= 0) return "Trial used up"
            val minutes = (remaining / 60).toInt()
            val seconds = remaining.toInt() % 60
            return if (minutes >= 1) "$minutes min ${seconds}s left in trial"
            else "${seconds}s left in trial"
        }

    /** Can the user record right now? Pro = always; trial = only if not exhausted. */
    val canRecord: Boolean
        get() = _isPro.value || !trialExhausted

    // MARK: - Unlock via JVZoo license

    suspend fun redeemLicense(key: String): LicenseService.Result {
        val entered = key.trim()
        if (entered.isEmpty()) return LicenseService.Result.Invalid("Enter your unlock code")

        // Simple shared-code unlock (no server needed). Compare case-insensitively
        // so "yaptext-pro-2026" and "YAPTEXT-PRO-2026" both work.
        return if (entered.equals(Config.PRO_UNLOCK_CODE, ignoreCase = true)) {
            AppStorage.isPro = true
            AppStorage.licenseKey = entered
            _isPro.value = true
            LicenseService.Result.Valid
        } else {
            LicenseService.Result.Invalid("That code wasn't recognized. Check the code from your purchase.")
        }
    }
}
