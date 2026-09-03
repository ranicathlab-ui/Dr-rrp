package com.postpci.drrrp.data.auth

import com.postpci.drrrp.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

private data class FakeAccount(
    val uid: String,
    val email: String,
    var password: String?,
    val displayName: String,
    val role: UserRole,
    val linkedPatientId: String? = null,
)

/**
 * In-memory stand-in for Firebase Auth + the Firestore role field — see [AuthGateway] for the
 * real-implementation plan. State is process-lifetime only (no disk persistence), which is fine
 * for a stub: a real Firebase session would survive process death, this one doesn't.
 *
 * Seeded with one staff account (signs in directly) and one patient / one caregiver invite
 * (need [completeFirstLogin] first, exactly like a staff-issued invite would).
 */
class FakeAuthGateway : AuthGateway {
    private val accounts = mutableMapOf<String, FakeAccount>() // keyed by email

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    private val _isSessionRestored = MutableStateFlow(true)
    override val isSessionRestored: StateFlow<Boolean> = _isSessionRestored

    companion object {
        const val DEMO_STAFF_EMAIL = "staff@aasaihealthcentre.test"
        const val DEMO_STAFF_PASSWORD = "staff123"
        const val STAFF1_EMAIL = "drprasad27@yahoo.co.in"
        const val STAFF2_EMAIL = "deepthibr@gmail.com"
        const val STAFF3_EMAIL = "dreswaran@gmail.com"
        const val STAFF_PASSWORD = "drrrpapp@2026"
        const val DEMO_PATIENT_EMAIL = "patient@example.test"
        const val DEMO_CAREGIVER_EMAIL = "caregiver@example.test"
        const val DEMO_PATIENT_ID = "demo-patient-1"
    }

    init {
        accounts[DEMO_STAFF_EMAIL] = FakeAccount(
            uid = "staff-1",
            email = DEMO_STAFF_EMAIL,
            password = DEMO_STAFF_PASSWORD,
            displayName = "Clinic Staff",
            role = UserRole.STAFF,
        )
        accounts[STAFF1_EMAIL] = FakeAccount(
            uid = "staff-dr-prasad",
            email = STAFF1_EMAIL,
            password = STAFF_PASSWORD,
            displayName = "Dr. A. Rajaram Prasad",
            role = UserRole.STAFF,
        )
        accounts[STAFF2_EMAIL] = FakeAccount(
            uid = "staff-dr-deepthi",
            email = STAFF2_EMAIL,
            password = STAFF_PASSWORD,
            displayName = "Dr. Deepthi B R",
            role = UserRole.STAFF,
        )
        accounts[STAFF3_EMAIL] = FakeAccount(
            uid = "staff-dr-eswaran",
            email = STAFF3_EMAIL,
            password = STAFF_PASSWORD,
            displayName = "Dr. Eswaran",
            role = UserRole.STAFF,
        )
        accounts[DEMO_PATIENT_EMAIL] = FakeAccount(
            uid = DEMO_PATIENT_ID,
            email = DEMO_PATIENT_EMAIL,
            password = null, // needs first-login setup, like a real staff-issued invite
            displayName = "Demo Patient",
            role = UserRole.PATIENT,
        )
        accounts[DEMO_CAREGIVER_EMAIL] = FakeAccount(
            uid = "caregiver-1",
            email = DEMO_CAREGIVER_EMAIL,
            password = null,
            displayName = "Demo Caregiver",
            role = UserRole.CAREGIVER,
            linkedPatientId = DEMO_PATIENT_ID,
        )
    }

    override suspend fun signIn(email: String, password: String, allowedRoles: Set<UserRole>?): SignInResult {
        val account = accounts[email.trim().lowercase()]
            ?: return SignInResult.Error("No account found for this email.")
        if (account.password == null) {
            return SignInResult.NeedsPasswordSetup(account.email)
        }
        if (account.password != password) {
            return SignInResult.Error("Incorrect password.")
        }
        val user = account.toAuthUser()
        if (allowedRoles != null && user.role !in allowedRoles) {
            val msg = if (user.role == UserRole.STAFF) {
                "This is a Clinical Staff account. Please switch to the 'Clinical Staff' tab to sign in."
            } else if (user.role == UserRole.CAREGIVER) {
                "This is a Caregiver account. Please switch to the 'Patients' tab to sign in."
            } else {
                "This is a Patient account. Please switch to the 'Patients' tab to sign in."
            }
            return SignInResult.Error(msg)
        }
        _currentUser.value = user
        return SignInResult.Success(user)
    }

    override suspend fun completeFirstLogin(email: String, newPassword: String, allowedRoles: Set<UserRole>?): AuthOpResult {
        val account = accounts[email.trim().lowercase()]
            ?: return AuthOpResult.Error("No invite found for this email.")
        if (newPassword.length < 6) {
            return AuthOpResult.Error("Password must be at least 6 characters.")
        }
        val user = account.toAuthUser()
        if (allowedRoles != null && user.role !in allowedRoles) {
            val msg = if (user.role == UserRole.STAFF) {
                "This is a Clinical Staff account. Please switch to the 'Clinical Staff' tab to sign in."
            } else if (user.role == UserRole.CAREGIVER) {
                "This is a Caregiver account. Please switch to the 'Patients' tab to sign in."
            } else {
                "This is a Patient account. Please switch to the 'Patients' tab to sign in."
            }
            return AuthOpResult.Error(msg)
        }
        account.password = newPassword
        _currentUser.value = user
        return AuthOpResult.Success
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }

    override suspend fun createPatientInvite(name: String, contact: String, email: String?, password: String): InviteCredentials {
        val resolvedEmail = email?.ifBlank { null } ?: syntheticEmail(name)
        val uid = UUID.randomUUID().toString()
        accounts[resolvedEmail] = FakeAccount(
            uid = uid,
            email = resolvedEmail,
            password = null,
            displayName = name,
            role = UserRole.PATIENT,
        )
        return InviteCredentials(uid, resolvedEmail, password, emailSent = !email.isNullOrBlank())
    }

    override suspend fun createCaregiverInvite(name: String, contact: String, patientId: String, email: String?, password: String): InviteCredentials {
        val resolvedEmail = email?.ifBlank { null } ?: syntheticEmail(name)
        val uid = UUID.randomUUID().toString()
        accounts[resolvedEmail] = FakeAccount(
            uid = uid,
            email = resolvedEmail,
            password = null,
            displayName = name,
            role = UserRole.CAREGIVER,
            linkedPatientId = patientId,
        )
        return InviteCredentials(uid, resolvedEmail, password, emailSent = !email.isNullOrBlank())
    }

    override suspend fun getIdToken(): String? = currentUser.value?.let { "fake-id-token-${it.uid}" }

    private fun FakeAccount.toAuthUser() = AuthUser(uid, email, displayName, role, linkedPatientId)

    private fun syntheticEmail(name: String): String {
        val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.')
        return "$slug.${UUID.randomUUID().toString().take(4)}@invite.drrrp.test"
    }

    private fun generateTempPassword(): String = UUID.randomUUID().toString().take(8)
}
