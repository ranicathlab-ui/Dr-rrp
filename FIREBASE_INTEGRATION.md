# Backend integration — what was built (2026-09-01 → 09-02)

Summary of the auth/backend work, for review/adjustment. Firebase project: `dr-rrp-app-9f517`. Live backend: `https://dr-rrp-aasai-backend.onrender.com`.

## 1. Package rename

`com.example.drrrp` → **`com.postpci.drrrp`** (matches what got registered in the Firebase console). Renamed everywhere: `namespace`/`applicationId` in `app/build.gradle.kts`, all source files' `package`/`import` lines, and the `main`/`test`/`androidTest` directory trees.

## 2. Real Firebase Auth

- **`app/google-services.json`** — added (package `com.postpci.drrrp`), gitignored (not committed).
- **`app/build.gradle.kts`** — `com.google.gms.google-services` plugin enabled; `firebase-bom`, `firebase-auth`, `firebase-firestore`, `firebase-messaging`, `kotlinx-coroutines-play-services`. (`firebase-functions` was added then later removed — see §4.)
- **`data/auth/FirebaseAuthGateway.kt`** — real `AuthGateway`: email/password sign-in, role/`linkedPatientId`/`canLogEntries` read from a Firestore `users/{uid}` doc, first-login password setup (`mustChangePassword` flag), sign-out, ID token for the REST `AuthInterceptor`.
- **`DrRrpApplication.kt`** — `authGateway` binding is `FirebaseAuthGateway`. `FakeAuthGateway` is left in the codebase as a test/preview double.

**Firestore schema, `users/{uid}`:**

| field | type | notes |
|---|---|---|
| `role` | string | `"STAFF"` \| `"PATIENT"` \| `"CAREGIVER"` |
| `displayName` | string | |
| `mustChangePassword` | bool | true until first-login password is set |
| `linkedPatientId` | string? | caregivers only |
| `canLogEntries` | bool? | caregivers only — false makes their app read-only; defaults true if unset |
| `fcmTokens` | string[]? | registered push tokens, written by `POST auth/register-device` |

## 3. Firestore security rules — **deployed and live**

**`firestore.rules`** covers `users/{uid}` and the whole `patients/**` tree (baseline, entries, bleedingEvents, alerts, messages). In practice these are defense-in-depth: all real reads/writes go through the backend's Admin SDK, which bypasses rules entirely — the rules exist for the case someone points the Firestore client SDK directly at the database with a real user's ID token, bypassing the REST layer.

## 4. The backend — **Cloud Functions was abandoned; Render is the live implementation**

**First attempt (2026-09-01–02): Firebase Cloud Functions.** `functions/index.js` implemented invite creation, the full REST API (as an Express app behind one HTTPS function + a Hosting rewrite), Firestore-triggered alert generation, and a scheduled missed-entry check. This was fully built, smoke-tested against the local Firebase emulator (14/14 checks passed), but **deployment requires the Blaze billing plan** — Cloud Functions builds via Cloud Build and stores images in Artifact Registry, both GCP-wide paid APIs that need a billing account attached before Google activates them, regardless of actual usage. The user has no way to enable billing.

**Pivot: standalone Express server on Render (free, no billing anywhere).** `server/index.js` is the same logic, restructured to run as a plain Node process instead:
- Authenticates to Firebase via a **service account key** (also free — created via IAM, no billing) instead of Cloud Functions' automatic credentials.
- Firestore trigger logic got **inlined directly into the request handlers** (this server sees every write as it happens — no separate event-trigger mechanism needed).
- The Cloud Scheduler missed-entry job became a plain `POST /internal/check-missed-entries` endpoint, secret-protected (`MISSED_ENTRY_CRON_SECRET`), meant to be pinged daily by a free external cron service (cron-job.org).
- `FIREBASE_SERVICE_ACCOUNT_JSON` accepts either raw JSON or base64 — Render's env var paste box auto-detects and splits pasted JSON into one variable per top-level key, so base64 (no `{`, `"`, `:`, newlines to trigger that) is what actually works there in practice.
- Re-verified with the same 7-check smoke test against the standalone server + emulator after the rewrite — all passed, including the full invite → first-login → post-entry → server-alert → staff-sees-it flow.
- `functions/` is kept in the repo for reference only (marked "NOT CURRENTLY USED" in its header) in case Blaze ever becomes available later — it is **not** kept in sync with `server/` going forward.

**Invite creation** (`createPatientInvite`/`createCaregiverInvite`) moved from Firebase callable Cloud Functions to plain REST endpoints (`POST /invite/patient`, `POST /invite/caregiver`) on the same Render server. `FirebaseAuthGateway.kt` calls them via a small dedicated Retrofit client (`InviteApiService`/`InviteApiProvider`) instead of the Firebase Functions SDK — that SDK dependency was removed from the Android app entirely.

**Deploy pipeline:** this project (`D:\post pci app`) is now a git repo, pushed to `https://github.com/ranicathlab-ui/Dr-rrp`. Render's `dr-rrp-aasai-backend` service deploys from that repo's `server/` subdirectory on every push to `main`.

## 5. FCM push notifications — **send side now live too**

- **`data/sync/DrRrpMessagingService.kt`** — receives pushes, posts to one of two notification channels by a `severity` data key (`EMERGENCY` → high-importance channel, everything else → routine).
- **`DrRrpApplication.kt`** — creates the two notification channels on startup; (re-)registers the FCM token with the backend on sign-in and token rotation.
- **Send side**, in `server/index.js`: a new alert (from a daily entry, a bleeding event, or the missed-entry sweep) and a new message both trigger a push to the relevant patient/caregiver/staff, inline in the same request handler that created the record.

## 6. Caregiver invite UI + `canLogEntries` enforcement

- **`ui/staff/caregiver/AddCaregiverViewModel.kt`**/**`AddCaregiverScreen.kt`** — staff-facing screen to link a caregiver to a patient (was entirely missing before).
- **`canLogEntries`** is now read from the caregiver's `AuthUser` and enforced both client-side (Today becomes read-only — disabled field cards, disabled medication checkboxes, no Log Entry sheet) and server-side in every write endpoint (defense in depth).

## 7. Contact number / emergency screen changes

- `ClinicContact.PHONE_NUMBER` is now **+919894184664** (Dr. Rajaram Prasad's real line), not a placeholder.
- The **"Call 108"** emergency-services action was removed entirely. Every contact action in the app — routine banner, Profile card, and the emergency-escalation screen's single primary button — now dials the same number under the label `ClinicContact.CONTACT_LABEL` = "Contact Dr. Rajaram Prasad".

## 8. Test coverage

Added on top of the existing `AlertRules`/`MonitoringSchedule` unit tests: `EmergencyGateViewModelTest` (which severities trigger the full-screen takeover, most-recent-wins, dismiss semantics, cross-patient isolation — using in-memory fakes of the three DAOs, no Android/Robolectric needed) and `ClinicContactTest` (a regression guard on the phone number/label themselves).

## Deployment status

| Piece | Status |
|---|---|
| Firestore database + rules | ✅ deployed, live |
| Firebase Auth | ✅ live |
| Backend (Render) | ✅ **live** at `dr-rrp-aasai-backend.onrender.com` |
| Invite creation | ✅ live (REST, not Cloud Functions) |
| FCM push, send + receive | ✅ live |
| `canLogEntries` enforcement | ✅ live, client + server |
| Android build | ✅ `assembleDebug` + `testDebugUnitTest` both pass |
| Git / GitHub | ✅ pushed to `github.com/ranicathlab-ui/Dr-rrp` |

## Still open

1. **Seed the first STAFF account** manually (Firebase Console → Authentication → add user, then a matching `users/{uid}` Firestore doc with `role: "STAFF"`, `mustChangePassword: false`) — nothing can create the *first* account, since invite creation itself requires an existing staff account. As of last check, **zero accounts exist** in the live project.
2. **Set up the daily cron ping** for the missed-entry sweep — cron-job.org (free), `POST https://dr-rrp-aasai-backend.onrender.com/internal/check-missed-entries` with header `X-Cron-Secret: <the value set in Render's env vars>`, once a day.
3. **Render free-tier cold starts** — the server sleeps after ~15 minutes idle, takes 30–50s to wake on the next request. Not broken, just occasionally slow.
4. **Firebase CLI / GitHub auth note (this machine):** interactive browser-based logins (`firebase login`, `git push` via Git Credential Manager) reliably hang in this environment — both from Claude Code's shell and from a normal terminal window. What actually works: a GitHub Personal Access Token (short-lived, `repo` scope, revoked right after use) for `git push`; the Firebase CLI's device-code-style login did eventually work from a real terminal window early on and has persisted since.
