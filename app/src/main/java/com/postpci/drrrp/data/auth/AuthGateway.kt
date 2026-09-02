package com.postpci.drrrp.data.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Identity boundary for the whole app. Firebase Auth (email/password) is the real source of
 * truth, with a custom role field (patient/staff/caregiver) read from Firestore alongside the
 * Firebase user, and the REST backend trusting the verified Firebase ID token.
 *
 * [FirebaseAuthGateway] is the live implementation, bound in
 * [com.postpci.drrrp.DrRrpApplication]. [FakeAuthGateway] remains as an in-memory stand-in —
 * useful for tests/previews, or swapping back in if Firebase access isn't available locally.
 */
interface AuthGateway {
    /** Null when signed out. Screens observe this to route between the login and app graphs. */
    val currentUser: StateFlow<AuthUser?>

    suspend fun signIn(email: String, password: String): SignInResult

    /** Patient/caregiver first-login flow: they set their own password from a staff-issued invite. */
    suspend fun completeFirstLogin(email: String, newPassword: String): AuthOpResult

    suspend fun signOut()

    /** Staff-only: creates a patient record's login and returns the invite to hand over. When
     *  [email] is a real address (not blank), the patient logs in with it directly and gets a
     *  Firebase password-reset email sent to it — see [InviteCredentials.emailSent]. Left blank,
     *  falls back to a synthetic login address and the returned temporary password, for patients
     *  without email access. */
    suspend fun createPatientInvite(name: String, contact: String, email: String? = null): InviteCredentials

    /** Staff-only: links a caregiver invite to an existing patient. Same [email] behavior as
     *  [createPatientInvite]. */
    suspend fun createCaregiverInvite(name: String, contact: String, patientId: String, email: String? = null): InviteCredentials

    /**
     * The token the REST backend verifies on every call (see the sync layer's `AuthInterceptor`).
     * A real implementation returns `FirebaseUser.getIdToken(false)`; null when signed out.
     */
    suspend fun getIdToken(): String?
}
