# Firebase integration — what was built (2026-09-01 → 09-02)

Summary of the Firebase wiring done this round, for review/adjustment. Project: `dr-rrp-app-9f517`.

## 1. Package rename

`com.example.drrrp` → **`com.postpci.drrrp`** (matches what got registered in the Firebase console). Renamed everywhere: `namespace`/`applicationId` in `app/build.gradle.kts`, all 72 source files' `package`/`import` lines, and the `main`/`test`/`androidTest` directory trees.

## 2. Real Firebase Auth

- **`app/google-services.json`** — added (package `com.postpci.drrrp`).
- **`app/build.gradle.kts`** — `com.google.gms.google-services` plugin enabled; added `firebase-bom` (34.11.0), `firebase-auth`, `firebase-firestore`, `firebase-functions`, `firebase-messaging`, `kotlinx-coroutines-play-services` (all also added to `gradle/libs.versions.toml`).
- **`data/auth/FirebaseAuthGateway.kt`** *(new)* — real `AuthGateway` implementation: email/password sign-in via Firebase Auth, role/`linkedPatientId` read from a Firestore `users/{uid}` doc, first-login password setup (`mustChangePassword` flag), sign-out, ID token for the REST `AuthInterceptor`.
- **`DrRrpApplication.kt`** — `authGateway` binding swapped from `FakeAuthGateway` to `FirebaseAuthGateway`. `FakeAuthGateway` is left in the codebase as a test/preview double.
- **`data/auth/AuthGateway.kt`** — doc comment updated to match.

**Firestore schema, `users/{uid}`:**

| field | type | notes |
|---|---|---|
| `role` | string | `"STAFF"` \| `"PATIENT"` \| `"CAREGIVER"` |
| `displayName` | string | |
| `mustChangePassword` | bool | true until first-login password is set |
| `linkedPatientId` | string? | caregivers only |
| `canLogEntries` | bool? | placeholder — nothing reads/writes it yet |

## 3. Firestore security rules — **deployed and live**

**`firestore.rules`** *(new)* — covers only the `users/{uid}` collection (the only Firestore collection this app actually uses; patient data goes through the REST sync layer, not Firestore — see §5). Self-read, staff-read-any, no client-side create (Cloud Function only), self-update limited to the `mustChangePassword` field.

## 4. Invite creation — Cloud Function (code done, **not yet deployed**)

The Firebase Auth *client* SDK can't create another user's account, so invite creation needed a server-side (Admin SDK) function:

- **`functions/index.js`** *(new)*, **`functions/package.json`** *(new)* — `createPatientInvite` and `createCaregiverInvite`, both callable, staff-only (checked against the caller's `users/{uid}.role` server-side). Generate a synthetic email + temp password, create the Auth user, write the `users/{uid}` doc.
- **`firebase.json`**, **`.firebaserc`** *(new)* — CLI project config.
- **`FirebaseAuthGateway.kt`** — `createPatientInvite`/`createCaregiverInvite` call these functions via `FirebaseFunctions` instead of throwing.

**Blocked on:** the project needs the **Blaze** billing plan to deploy any Cloud Function — see "Still open" below.

## 5. FCM push notifications

- **`data/sync/DrRrpMessagingService.kt`** *(new)* — receives pushes, posts to one of two notification channels by a `severity` data key (`EMERGENCY` → high-importance channel, everything else → routine). Tapping opens `MainActivity`.
- **`res/drawable/ic_notification.xml`** *(new)* — small-icon bell glyph for the status bar.
- **`DrRrpApplication.kt`** — creates the two notification channels on startup; observes `authGateway.currentUser` and (re-)registers the FCM token with the backend (`POST auth/register-device`, already in `SyncApiService`) on sign-in and on token rotation.
- **`MainActivity.kt`** — requests the `POST_NOTIFICATIONS` runtime permission on Android 13+.
- **`AndroidManifest.xml`** — `POST_NOTIFICATIONS` permission, `DrRrpMessagingService` registered.

**Note:** no real backend exists to *send* these pushes yet — the payload contract above (`title`/`body`/`severity` data keys) is a placeholder until a real one is settled on server-side.

## 6. Caregiver invite UI (was missing entirely)

`createCaregiverInvite` had no caller anywhere in the app. Added:

- **`ui/staff/caregiver/AddCaregiverViewModel.kt`**, **`AddCaregiverScreen.kt`** *(new)* — name + contact in, creates the invite, shows the returned email/temp password to hand to the caregiver (mirrors the existing patient-invite card in the baseline wizard).
- **`ui/staff/dashboard/PatientDetailScreen.kt`** — added an "Add caregiver" button.
- **`ui/staff/StaffShell.kt`** — new `AddCaregiver` screen state wired into the shell's in-house navigation.

## Deployment status

| Piece | Status |
|---|---|
| Firestore database | ✅ created |
| Firestore rules | ✅ deployed |
| Cloud Functions | ⛔ blocked — needs Blaze plan (see below) |
| FCM | ✅ client wired; nothing server-side sends pushes yet |
| Android build | ✅ `assembleDebug` + `testDebugUnitTest` both pass |

## Still open

1. **Enable Blaze billing**, then deploy functions:
   ```
   cd "D:\post pci app"
   npx firebase-tools deploy --only functions --project dr-rrp-app-9f517
   ```
   (Everything's already staged — `functions/index.js` written, `npm install` already run.)
2. **Seed a first STAFF account** manually (Authentication tab + a matching `users/{uid}` Firestore doc with `role: "STAFF"`, `mustChangePassword: false`) — nothing can create the *first* account, since invite creation itself requires an existing staff account.
3. **No real REST backend** — `SyncApiProvider.USE_REAL_BACKEND` is still `false`, `RetrofitSyncApiService.BASE_URL` is a placeholder (`https://api.drrrp.example.com/`). This is a separate server project, not scoped yet.
4. **Firebase CLI login note:** on this machine, `firebase login` only succeeds from a terminal window you open yourself — it fails when run through Claude Code's own shell (Google rejects the OAuth flow in that context). Deploys and other CLI commands work fine from either once logged in, since the login persists on-machine.
