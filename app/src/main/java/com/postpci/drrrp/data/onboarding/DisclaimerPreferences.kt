package com.postpci.drrrp.data.onboarding

import android.content.Context

/**
 * Persists whether this device has ever acknowledged [com.postpci.drrrp.ui.onboarding.DisclaimerScreen]
 * — the mandatory "this is a monitoring tool, not an emergency response system" notice Google
 * Play's health-app review expects before a patient can reach sign-in. Deliberately device-scoped
 * (not per-account): the same physical phone shouldn't need to re-acknowledge it just because a
 * different patient or caregiver signs in on it, and it must be checkable *before* any sign-in
 * has happened. Plain (unencrypted) SharedPreferences is fine here — this stores one non-sensitive
 * boolean, nothing a compromised device's storage would meaningfully expose.
 */
class DisclaimerPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hasAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_ACKNOWLEDGED, false)
        set(value) = prefs.edit().putBoolean(KEY_ACKNOWLEDGED, value).apply()

    private companion object {
        const val PREFS_NAME = "disclaimer_prefs"
        const val KEY_ACKNOWLEDGED = "has_acknowledged_disclaimer"
    }
}
