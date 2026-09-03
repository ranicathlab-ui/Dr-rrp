package com.postpci.drrrp.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.postpci.drrrp.data.model.UserRole
import com.postpci.drrrp.data.sync.CreateCaregiverInviteRequest
import com.postpci.drrrp.data.sync.CreatePatientInviteRequest
import com.postpci.drrrp.data.sync.InviteApiProvider
import com.postpci.drrrp.data.sync.InviteApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Real [AuthGateway] backed by Firebase Auth (email/password) plus a Firestore `users/{uid}`
 * document for the role field, per the plan in [AuthGateway]'s doc.
 *
 * Firestore schema for `users/{uid}`:
 *  - `role`: String, a [UserRole] name (e.g. "STAFF")
 *  - `displayName`: String
 *  - `linkedPatientId`: String? (caregivers only)
 *  - `mustChangePassword`: Boolean — true for a staff-issued invite that hasn't set its own
 *    password yet. A real invite can't leave the Auth account passwordless (Firebase Auth has
 *    no such state), so staff hand over a one-time temp password instead; [signIn] with that
 *    temp password succeeds at the Auth layer, then this flag routes the caller to
 *    [SignInResult.NeedsPasswordSetup] just like [FakeAuthGateway]'s null-password accounts did.
 *
 * [createPatientInvite] and [createCaregiverInvite] call the `/invite/patient`/`/invite/caregiver`
 * endpoints on the standalone backend (see `server/index.js`) rather than the Auth SDK directly:
 * a signed-in staff member's client can't create a *different* user's Auth account itself (the
 * client SDK only manages the current session), so that server runs with the Admin SDK instead,
 * gated staff-only server-side. (These used to be Firebase callable Cloud Functions — moved to
 * plain REST because Cloud Functions needs the Blaze billing plan, which isn't available here;
 * see `server/index.js`'s doc comment for the full story.)
 */
class FirebaseAuthGateway(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AuthGateway {

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    // Built lazily, referencing this instance as its own AuthGateway (for AuthInterceptor) — by
    // the time an invite call actually happens, sign-in has long since completed, so getIdToken()
    // works fine despite the apparent self-reference. See InviteApiProvider's doc for why this
    // isn't just SyncApiService plus two more methods (a circular-dependency issue).
    private val inviteApi: InviteApiService by lazy { InviteApiProvider.create(this) }

    // For restoreSession() below — a one-off suspend round-trip on cold start, not tied to any
    // screen's lifecycle.
    private val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Firebase Auth persists the session across process restarts/kills and restores
        // auth.currentUser before this even runs — without this, a killed-and-relaunched app
        // would show the login screen despite the user never having signed out. The listener
        // below only ever clears state on an explicit sign-out; populating it from a *restored*
        // session needs a suspend round-trip to Firestore for the role doc, which the listener
        // callback (non-suspend) can't make — hence the separate one-shot restore here.
        auth.currentUser?.let { restoreSession(it) }

        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser == null) {
                _currentUser.value = null
            }
        }
    }

    private fun restoreSession(firebaseUser: FirebaseUser) {
        restoreScope.launch {
            try {
                val doc = firestore.collection(USERS_COLLECTION).document(firebaseUser.uid).get().await()
                if (doc.exists() && doc.getBoolean(FIELD_MUST_CHANGE_PASSWORD) != true) {
                    doc.toAuthUser(firebaseUser.uid, firebaseUser.email ?: "")?.let { _currentUser.value = it }
                }
                // If the doc is missing, incomplete, or mustChangePassword is still true, this
                // silently leaves _currentUser null — the user just sees the login screen, same
                // as if they'd never had a session. Nothing destructive either way.
            } catch (e: Exception) {
                // Offline on cold start, etc. — same fallback: login screen, try again later.
            }
        }
    }

    override suspend fun signIn(email: String, password: String, allowedRoles: Set<UserRole>?): SignInResult {
        val trimmedEmail = email.trim()
        val authResult = try {
            auth.signInWithEmailAndPassword(trimmedEmail, password).await()
        } catch (e: Exception) {
            return SignInResult.Error(e.message ?: "Sign-in failed.")
        }
        val uid = authResult.user?.uid ?: return SignInResult.Error("Sign-in failed.")
        val doc = try {
            firestore.collection(USERS_COLLECTION).document(uid).get().await()
        } catch (e: Exception) {
            return SignInResult.Error(e.message ?: "Could not load account details.")
        }
        if (!doc.exists()) {
            return SignInResult.Error("No account record found for this user.")
        }
        if (doc.getBoolean(FIELD_MUST_CHANGE_PASSWORD) == true) {
            return SignInResult.NeedsPasswordSetup(trimmedEmail)
        }
        val user = doc.toAuthUser(uid, authResult.user?.email ?: trimmedEmail)
            ?: return SignInResult.Error("Account record is incomplete.")

        if (allowedRoles != null && user.role !in allowedRoles) {
            auth.signOut()
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
        if (newPassword.length < 6) {
            return AuthOpResult.Error("Password must be at least 6 characters.")
        }
        // Relies on signIn(email, tempPassword) having just run: updatePassword only works on
        // the currently signed-in FirebaseUser, so the temp-password sign-in must come first.
        val firebaseUser = auth.currentUser
            ?: return AuthOpResult.Error("Sign in with the temporary password first.")
        return try {
            firebaseUser.updatePassword(newPassword).await()
            val uid = firebaseUser.uid
            val userDoc = firestore.collection(USERS_COLLECTION).document(uid)
            userDoc.update(FIELD_MUST_CHANGE_PASSWORD, false).await()
            val user = userDoc.get().await().toAuthUser(uid, firebaseUser.email ?: email.trim())
                ?: return AuthOpResult.Error("Account record is incomplete.")

            if (allowedRoles != null && user.role !in allowedRoles) {
                auth.signOut()
                val msg = if (user.role == UserRole.STAFF) {
                    "This is a Clinical Staff account. Please switch to the 'Clinical Staff' tab to sign in."
                } else if (user.role == UserRole.CAREGIVER) {
                    "This is a Caregiver account. Please switch to the 'Patients' tab to sign in."
                } else {
                    "This is a Patient account. Please switch to the 'Patients' tab to sign in."
                }
                return AuthOpResult.Error(msg)
            }

            _currentUser.value = user
            AuthOpResult.Success
        } catch (e: Exception) {
            AuthOpResult.Error(e.message ?: "Could not set password.")
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }

    // contact isn't sent to the backend today — there's no SMS/notification channel yet to use
    // it for, same as FakeAuthGateway. It stays in the interface for when that lands.
    override suspend fun createPatientInvite(name: String, contact: String, email: String?, password: String): InviteCredentials {
        val response = try {
            inviteApi.createPatientInvite(CreatePatientInviteRequest(name, password, email?.ifBlank { null }))
        } catch (e: Exception) {
            throw IllegalStateException("createPatientInvite failed: ${e.message}", e)
        }
        val emailSent = trySendPasswordResetEmail(email)
        return InviteCredentials(response.patientId, response.email, response.temporaryPassword, emailSent)
    }

    override suspend fun createCaregiverInvite(name: String, contact: String, patientId: String, email: String?, password: String): InviteCredentials {
        val response = try {
            inviteApi.createCaregiverInvite(CreateCaregiverInviteRequest(name, patientId, password, contact.ifBlank { null }, email?.ifBlank { null }))
        } catch (e: Exception) {
            throw IllegalStateException("createCaregiverInvite failed: ${e.message}", e)
        }
        val emailSent = trySendPasswordResetEmail(email)
        return InviteCredentials(response.patientId, response.email, response.temporaryPassword, emailSent)
    }

    /** Best-effort: a failed reset email never fails the invite itself — the on-screen temporary
     *  password is always shown as a fallback (see InviteCredentials' doc). Uses the client SDK's
     *  own "forgot password" mechanism against the just-created Firebase Auth account, which is
     *  a free, built-in Firebase feature — no third-party email/SMS service involved. */
    private suspend fun trySendPasswordResetEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return try {
            auth.sendPasswordResetEmail(email).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getIdToken(): String? =
        auth.currentUser?.getIdToken(false)?.await()?.token

    private fun DocumentSnapshot.toAuthUser(uid: String, email: String): AuthUser? {
        val role = getString(FIELD_ROLE)?.let {
            try {
                UserRole.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: return null
        return AuthUser(
            uid = uid,
            email = email,
            displayName = getString(FIELD_DISPLAY_NAME) ?: email,
            role = role,
            linkedPatientId = getString(FIELD_LINKED_PATIENT_ID),
            canLogEntries = getBoolean(FIELD_CAN_LOG_ENTRIES) ?: true,
        )
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val FIELD_ROLE = "role"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_LINKED_PATIENT_ID = "linkedPatientId"
        const val FIELD_CAN_LOG_ENTRIES = "canLogEntries"
        const val FIELD_MUST_CHANGE_PASSWORD = "mustChangePassword"
    }
}
