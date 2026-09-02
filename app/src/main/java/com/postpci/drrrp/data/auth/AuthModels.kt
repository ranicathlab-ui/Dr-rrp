package com.postpci.drrrp.data.auth

import com.postpci.drrrp.data.model.UserRole

/**
 * Mirrors what a real implementation gets by combining the Firebase Auth user with the
 * custom role field stored alongside it in Firestore (see [AuthGateway] doc).
 */
data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    /** Set for a caregiver: the single patient they're linked to. */
    val linkedPatientId: String? = null,
    /** Caregivers only — false makes their app read-only (see PatientCaregiverShell). Always
     *  true for a patient logging their own data. Defaults to true when unset, matching the
     *  backend's own default (see functions/lib/auth.js). */
    val canLogEntries: Boolean = true,
)

sealed interface SignInResult {
    data class Success(val user: AuthUser) : SignInResult

    /** Staff-issued invite account that hasn't had its password set yet. */
    data class NeedsPasswordSetup(val email: String) : SignInResult

    data class Error(val message: String) : SignInResult
}

sealed interface AuthOpResult {
    data object Success : AuthOpResult

    data class Error(val message: String) : AuthOpResult
}

/**
 * Credentials staff hand to a new patient/caregiver to complete their first login, plus the
 * [patientId] (the auth uid) staff need immediately to link a local record — e.g. save the
 * baseline wizard's draft — without a separate lookup round-trip.
 *
 * [emailSent] is true when [email] is the patient/caregiver's own real address and a Firebase
 * password-reset email was successfully sent to it — the on-screen [temporaryPassword] is then
 * just a fallback (e.g. the email lands in spam), not the primary path. False for a synthetic
 * `@invite.drrrp.test` address, where staff must relay the temporary password directly.
 */
data class InviteCredentials(val patientId: String, val email: String, val temporaryPassword: String, val emailSent: Boolean = false)
