# DR RRP — Project Handoff Document

**Purpose of this document:** full context for continuing work on this project in a new AI session (e.g. Google Gemini). Written to be self-contained — read this before touching any code.

---

## 1. What this project is

**DR RRP** is a post-PCI (angioplasty) cardiac recovery monitoring Android app built for **Aasai Health Centre, Salem, India**, under **Dr. A. Rajaram Prasad**. Three user roles:

- **Patient** — logs daily vitals/symptoms (BP, heart rate, SpO2, weight, chest pain, breathlessness, access-site check, activity, medication adherence), sees trends, receives alerts, messages the clinic.
- **Caregiver** — linked to one patient, can log on their behalf (unless set read-only by staff), otherwise same view as the patient.
- **Staff** (clinic) — manages a patient list, creates patient/caregiver accounts, edits baseline procedural data (a 5-step wizard: Demographics → Procedural → Labs & Vitals → Medications & Follow-up → Social), reviews alerts, messages patients, manages caregiver permissions.

**Package name:** `com.postpci.drrrp`
**Repo:** https://github.com/ranicathlab-ui/Dr-rrp (branch `main`)
**Owner's email:** samkalai167@gmail.com (also the Firebase project owner and Render account owner)

---

## 2. Architecture

- **Client:** Kotlin + Jetpack Compose (Material 3), single-Activity, manual state-based navigation for the staff/patient shells (no Jetpack Navigation inside those — see §5 "ViewModelStore key collisions" for why this matters), Jetpack Navigation Compose only at the top level (`DrRrpNavHost`) for Disclaimer → Login → role-home routing.
- **Local storage:** Room, encrypted at rest via SQLCipher. **Offline-first**: every write lands in Room first (marked `PENDING`), then a `SyncManager` pushes to the backend and pulls other devices' changes down.
- **Auth:** Firebase Authentication (email/password). Firestore holds each user's `role`, `displayName`, `linkedPatientId` (caregivers), `canLogEntries` (caregivers), `mustChangePassword`.
- **Backend:** **NOT Firebase Cloud Functions** — a standalone Express/Node server (`server/index.js`) deployed on **Render.com's free tier**. This was a deliberate architecture pivot: Cloud Functions requires the Blaze billing plan (Cloud Build/Artifact Registry are billing-gated GCP APIs even at zero usage), and the clinic owner cannot/will not enable billing. The Render server authenticates to Firebase via a service-account key (Admin SDK), completely free. Firestore and Firebase Auth themselves are unaffected — only the *compute* moved off Cloud Functions.
  - Backend URL: `https://dr-rrp-aasai-backend.onrender.com`
  - **Important Render account note:** the owner's Render account has **7 dead/abandoned duplicate services** left over from early setup struggles (`dr-rrp-aasai-backend-36`, `dr-rrp-backend`, `Dr-RRP health monitoring`, etc., all showing "Failed deploy") sitting inside a project called "My project". **None of these are live.** The real, live service is `dr-rrp-aasai-backend`, listed separately under "Ungrouped Services". Don't be confused by the failed ones; don't delete the real one.
  - Env vars on Render: `FIREBASE_SERVICE_ACCOUNT_JSON` (base64-encoded service account JSON — Render's paste box mangles raw JSON, so it must be base64), `MISSED_ENTRY_CRON_SECRET` (shared secret for the missed-entry sweep endpoint), `PORT` (Render sets this).
- **Push:** Firebase Cloud Messaging, sent server-side from `server/lib/push.js`.
- **FCM push has never been verified received on a real device** — only tested that the send-side code runs without erroring. This needs a real device with a registered token to actually confirm.

### Repo layout
```
app/                          — Android app module
server/                       — the live Express backend (index.js + lib/{alertRules,auth,monitoringSchedule,push}.js)
server/README.md              — full Render deployment walkthrough
functions/                    — OLD Cloud Functions implementation, NOT deployed, NOT kept in sync — has a "NOT CURRENTLY USED" header. Ignore unless resurrecting Cloud Functions someday.
firebase.json                 — just firestore.rules config now (Hosting/Functions sections removed)
store-assets/                 — 512x512 Play Store icon
APP_OVERVIEW.md, FIREBASE_INTEGRATION.md — project docs, mostly current but written before the last session's changes — cross-check against this doc and the code
```

---

## 3. Current deployment state (as of this handoff)

**GitHub `main` (and therefore Render, which deploys from it) is at commit `95e1f33`.**

**Local working copy is AHEAD by 3 commits, all unpushed:**
```
2108382 Staff sets patient/caregiver passwords directly + fix a real crash found live
89fe61b Real patient/caregiver email + dual-audience login screen
c0f17d0 Add staff-side caregiver management (view + toggle canLogEntries)
```
Wait — `c0f17d0` **was** pushed and redeployed earlier in the session that produced this handoff (confirmed live via a direct API check). The two still genuinely unpushed are `89fe61b` and `2108382`.

**There is also uncommitted work in progress right now** (not yet committed, let alone pushed) in these 3 files, implementing keyboard-avoidance (auto-scroll focused fields above the soft keyboard) across the staff wizard and Add Caregiver screen:
```
M app/src/main/java/com/postpci/drrrp/ui/common/FormFields.kt
M app/src/main/java/com/postpci/drrrp/ui/staff/caregiver/AddCaregiverScreen.kt
M app/src/main/java/com/postpci/drrrp/ui/staff/wizard/BaselineWizardScreen.kt
```
This was mid-manual-testing on an emulator when this session ended. **Before doing anything else**, check `git diff` on these 3 files, finish verifying it (see §7), then commit and push along with the other two pending commits.

### What breaks/degrades until you push `89fe61b` + `2108382` and redeploy
The APK already installed on the owner's test device has client code ahead of the live server:
- Typing a real email for a new patient/caregiver — **silently ignored** by the live server; falls back to the old synthetic `@invite.drrrp.test` address. No password-reset email gets sent (Firebase's `sendPasswordResetEmail` fails silently because no user exists with that address).
- The new "Create password" / "Confirm password" fields in patient/caregiver creation — **silently ignored**; the live server still auto-generates its own random password.
Neither of these crashes anything — they just don't do what the UI implies until the server catches up. **Tell the user this plainly before they test those specific flows further.**

### GitHub push mechanics (important — read before pushing)
- Git Credential Manager **hangs indefinitely** in this environment (both in an agent's own shell and in the user's own terminal). Don't attempt it.
- The working method: ask the user for a **fresh GitHub Personal Access Token** each time (Settings → Developer settings → Personal access tokens → generate new → `repo` scope, or "Contents: Read and write" if fine-grained), then:
  ```
  git push "https://<TOKEN>@github.com/ranicathlab-ui/Dr-rrp.git" main
  ```
  Never persist the token to `.git/config` or any file — use it once, inline, then tell the user to revoke it immediately.
- After every push that touches `server/`, the user must manually redeploy on Render: dashboard → `dr-rrp-aasai-backend` service → **Manual Deploy → Deploy latest commit** → wait for "Your service is live" in the log.
- Firebase CLI login (if ever needed) only works from a **real terminal the user opens themselves** — fails identically in any agent shell. It persists on-device afterward.

---

## 4. Everything fixed/built in the most recent working session (chronological, so you understand *why* things look the way they do)

This list matters because several of these were genuine, high-severity bugs found through **live testing on an Android emulator via ADB automation**, not just code review — the fixes have specific reasoning baked into code comments. Read the comments before changing this code.

1. **Emergency-alert takeover never appeared** (the full-screen "This looks like an emergency" screen). Root cause: `androidx.lifecycle.ViewModelStore` maps a `key` string directly to an instance with **no per-class namespacing**. `EmergencyGateViewModel`, `TodayViewModel`, `TrendsViewModel`, and `AlertsViewModel` all used `viewModel(key = patientId, ...)` — the *same* bare key — so whichever ViewModel was constructed second on a screen visit silently evicted-and-cleared the first one via `ViewModelStore.put()`. Fixed by namespacing every key as `"ClassName:$patientId"`. **If you ever see a ViewModel's state mysteriously reset the instant a sibling screen composes, check for this exact bug pattern first.**

2. **Data-loss race in sync**: every screen's ViewModel calls `SyncManager.pullPatient()` directly on its own `init`, independent of the periodic WorkManager push job. Every pull-mapper stamps `syncStatus = SYNCED` unconditionally and `upsert()` replaces the whole row — so a pull racing ahead of a not-yet-pushed local write (e.g. marking an alert reviewed) could silently and *permanently* overwrite it, erasing the PENDING flag too. Fixed by making `pullPatient()` always push pending changes first, not just the periodic sync job.

3. **A single bad request could crash the entire backend.** No route in `server/index.js` had a try/catch; Express 4 doesn't auto-forward async rejections to error middleware. One specific trigger: `/alert/acknowledge/:alertId` ran a `collectionGroup("alerts").where("id", "==", ...)` query needing a Firestore composite index that was never created — every acknowledgment threw, unhandled, crashing the whole Node process, and Render then 502'd *every* concurrent client until restart, repeating on retry. Fixed with an `asyncHandler()` wrapper around every route + global Express error middleware + rewrote that route to take `:patientId` in the path (client already has it locally) instead of the cross-patient index-dependent search.

4. **Caregiver's contact number was collected in the UI then silently discarded** — never reached the request DTO. Wired through end-to-end (Android → server → Firestore).

5. **"No daily entries logged yet." could flash false during a slow initial load** on the staff patient-detail screen — a loading-state gating bug (`isLoadingPage` only got set *inside* `loadNextPage()`, leaving a gap during the preceding pull where the empty-state text showed prematurely).

6. **Two "no submit button" bugs**, both scroll-related:
   - `TodayScreen`'s outer content had no `verticalScroll` and used a `LazyVerticalGrid` nested inside it — for any patient with >4 due fields (normal), the "Finish today's check-in" button rendered entirely off-screen, unreachable. Fixed by replacing the `LazyVerticalGrid` with a plain chunked 2-column layout and adding scroll to the parent.
   - `LogEntrySheet`'s per-field bottom sheet had no scroll or `imePadding()` — multi-field entries (blood pressure, access-site's four toggles) plus the keyboard could push the Save button below the visible area.

7. **App icon** replaced (was a placeholder). Switched from adaptive-icon XML to plain legacy PNG mipmaps (asset pack provided didn't include proper adaptive layers).

8. **Google Play compliance work** — none of this existed before:
   - Mandatory disclaimer/consent screen shown once per device before login is reachable (non-diagnostic scope + emergency protocol text, gated by a checkbox), backed by a `DisclaimerPreferences` SharedPreferences flag.
   - Privacy Policy + Terms & Conditions, authored and published as hosted pages (see §6 for URLs) — linked from the disclaimer screen and a new Profile "Legal" section.
   - "Request account & data deletion" on Profile (sends a flagged message to staff via the existing messaging channel).
   - A 4–12 hour response-time caption on the messaging screen.
   - Removed a stale "Demo credentials (stub auth)" hint that was still showing fake login info on the real production login screen.
   - **Still outstanding, not code — the owner's own actions**: Play Console's "Health Apps" declaration, an Organization Developer Account + D-U-N-S number (personal accounts have high rejection rates for medical apps), CDSCO SaMD classification consideration (India), DPDP Act 2023 compliance review.

9. **Staff-side caregiver management** — previously, `canLogEntries` enforcement existed (client disables logging UI, server 403s a read-only caregiver's writes) but there was **no way for staff to ever actually set it to false** — no UI showed a patient's linked caregivers at all. Added `GET /patient/:patientId/caregivers` and `PUT /caregiver/:caregiverId/permissions` plus a "Caregivers" card on the staff patient-detail screen with a toggle.

10. **Real email + password-reset email for patients/caregivers.** Previously every account got a synthetic `name.xxxx@invite.drrrp.test` address with no real inbox — staff had to manually relay a random temp password. Now: an optional real email field, and if provided, `FirebaseAuthGateway` calls Firebase Auth's own free `sendPasswordResetEmail()` right after account creation — a real email lands with a working "set your password" link, no third-party email/SMS service. Left blank, falls back to the old synthetic-email + manual-relay flow.

11. **Dual-audience login screen redesign** — "Patients" / "Clinical Staff" tabs above the sign-in form (purely presentational; both submit through the same `LoginViewModel.submitSignIn` — role always comes from the Firestore doc, never the tab). Branding line changed from "DR RRP" to "Dr. A. Rajaram Prasad".

12. **Staff-chosen passwords.** The owner's actual complaint: *"while staff creating account there [is] none to create patients email and password after completion of patients details i dont know what to give to patient email and password."* Added "Create password" + "Confirm password" fields to both the wizard's Demographics step and Add Caregiver — staff now types the password directly instead of the app generating a random one. Server requires and validates it (min 6 chars) instead of generating.

13. **A real crash found live while testing #12**: `BaselineWizardViewModel.saveCurrentStepAndAdvance()` called the invite-creation network call inside `viewModelScope.launch` with **zero try/catch**. A slow/cold Render backend timing out on that call threw straight up through the coroutine, uncaught — crashing the entire app and wiping every field staff had just typed across all five wizard steps. (`AddCaregiverViewModel`'s equivalent call already had proper try/catch — this was an isolated gap.) Fixed: wrapped in try/catch/finally, surfaces a recoverable error message, leaves all typed data intact so retry is one tap.

14. **In progress, uncommitted, NOT visually verified**: keyboard-avoidance auto-scroll. The owner's literal request described classic Android View-system APIs (ScrollView/RecyclerView + soft-keyboard-height detection); the actual Compose-idiomatic fix is `BringIntoViewRequester` + `onFocusEvent` on each field, applied once in the shared `ui/common/FormFields.kt` helpers (`FormTextField`, `FormNumberField`, `FormDecimalField`, `FormPasswordField`) so all ~60 wizard fields plus Add Caregiver's fields get it for free, plus `imePadding()` on the containing scrollable layouts (`BaselineWizardScreen`'s `LazyColumn`, `AddCaregiverScreen`'s newly-added `Column` + `verticalScroll` — that screen had **no scroll at all** before this, a second instance of bug #6's pattern). This is the correct, standard approach for this exact problem in Compose and it compiles/runs without crashing, but **it has not been visually confirmed working** — see the emulator limitation in §5 below, which turned out to block confirming this specific fix (and, in retrospect, calls into question how rigorously the *earlier* keyboard-related fixes in #6 were actually visually verified too, for the same reason). Not yet committed. **Before claiming this is fixed to the owner, get it verified on a real physical device** — that's the one thing this whole session's testing setup couldn't do.

---

## 5. Testing methodology used throughout (if continuing to test live)

- Android emulator (`emulator-5554`) + `adb` at `/c/Users/Hp/AppData/Local/Android/Sdk/platform-tools/adb.exe` (Windows path; adjust for your environment).
- `uiautomator dump` + parsing the XML for exact element `bounds` before every `input tap` — screen coordinates shift between builds/states, never assume prior coordinates still apply.
- **Coordinate scaling gotcha**: screenshots pulled via `adb shell screencap` are at full device resolution (e.g. 1080×2400); if a description says "displayed at 900×2000", multiply by 1.2× to get real device pixels for tapping.
- `adb shell input text` **cannot handle spaces** — either use `%s` literally in place of spaces, or type words separately.
- `MSYS_NO_PATHCONV=1` needed before any `adb shell ...` command touching device paths like `/sdcard/...` when running in Git Bash on Windows, to stop MSYS from mangling them into Windows paths — but do **not** set it for `adb install`/`adb push`/`adb pull` commands referencing local (host) file paths, since that disables the necessary host-path conversion instead.
- `adb logcat -d --pid=$(adb shell pidof com.postpci.drrrp)` + grep for `FATAL EXCEPTION` is how every real crash in this session was actually caught — screenshots alone don't show crashes if the app respawns quickly.
- Render free-tier cold starts take ~15–50s after idle. A `SocketTimeoutException` in logcat during testing is often just this, not a real bug — but as bug #13 shows, whether an *unhandled* timeout crashes the app is very much a real bug worth checking every time one shows up.
- **Important limitation discovered late in this session, relevant to any keyboard/IME-related testing**: this emulator has a hardware keyboard bridged to it (forwarding the host machine's physical keyboard). When a hardware keyboard is present, Android's Gboard shows only its thin accessory toolbar (emoji/settings/mic icons) and suppresses the full on-screen QWERTY keyboard entirely — even after `adb shell settings put secure show_ime_with_hard_keyboard 1`. This means **no screenshot taken via this emulator all session ever showed the real, full-height soft keyboard** — so any earlier "verified visually" claim about a keyboard-covering-a-field fix (bug #6, and #14 above) was only confirmed for the *scroll infrastructure existing and not crashing*, not for the actual pixel-for-pixel "field stays above a real keyboard" behavior. If you need to genuinely confirm this, either fix the AVD's hardware-keyboard setting before booting it (Android Studio → AVD config → disable "Enable keyboard input"), or — more reliably — just test on the owner's real physical device.
- The `google-services.json` file is gitignored (contains Firebase project credentials) — it must already exist locally in `app/` for the build to work at all. If it's missing, the Android build will fail immediately; the file must be re-downloaded from Firebase Console (Project Settings → your Android app) and placed at `app/google-services.json`.

## 6. Live URLs / references

- Repo: https://github.com/ranicathlab-ui/Dr-rrp
- Backend: https://dr-rrp-aasai-backend.onrender.com (`/health` returns `{"ok":true}` when up)
- Privacy Policy (Claude Artifact, currently **private** — owner needs to click Share to make it public before it's usable in Play Console): https://claude.ai/code/artifact/5decb8ac-c36d-4cf3-a2c6-0ad93f6302ef
- Terms & Conditions (same private/share caveat): https://claude.ai/code/artifact/8cf26e58-316b-4177-a291-1fac9bdcdda0
- Clinic contact number shown throughout the app: **+91 98941 84664** (`ClinicContact.kt` — this is the single source of truth; the owner corrected this value twice during the session, so don't second-guess it against older docs)
- Doctor's full name for branding: **Dr. A. Rajaram Prasad**

## 7. Immediate next steps, in order

1. Review the 3 uncommitted files (keyboard-avoidance fix) — `git diff` them. The code is sound (see #14 in §4) but **not yet visually confirmed** — the emulator used couldn't show a real soft keyboard (see the callout in §5). Get it verified on the owner's actual physical device, not just an emulator, before telling them it's fixed.
2. Commit that fix.
3. Push all pending commits (`c0f17d0` already live; confirm, then push `89fe61b`, `2108382`, and the new keyboard-avoidance commit) using the PAT method in §3.
4. Ask the owner to redeploy on Render (Manual Deploy → Deploy latest commit).
5. Verify the real-email/staff-password/keyboard-avoidance flows work correctly end-to-end against the now-current server.
6. Resume the standing "keep testing the app" instruction — untested areas as of this handoff: `canLogEntries = false` (read-only) enforcement with an actual disabled caregiver account, staff acknowledging an alert via the dashboard UI end-to-end, FCM push actually arriving on a real device, the missed-entry cron sweep (cron-job.org ping to `/internal/check-missed-entries` — mentioned as still-open in FIREBASE_INTEGRATION.md, never set up).
7. Only when the owner explicitly asks: Play Store publishing steps (Organization Developer Account, D-U-N-S, Health Apps declaration, CDSCO SaMD classification). These are the owner's legal/business decisions — don't start them unprompted.

## 8. Working style notes for whoever picks this up

- The owner communicates in short, sometimes fragmentary messages (English is not their first language) and is not a developer — explanations should stay plain and concrete, avoid jargon, and confirm understanding of ambiguous requests before building rather than guessing wrong (this session asked clarifying questions twice and it paid off — once for "create/confirm password" which turned out to mean staff-chosen passwords, not a login self-registration flow).
- The owner has explicitly asked, more than once, to "keep testing the app" — meaning actually drive the running app and find real bugs, not just review code. Several of the most serious bugs in this project (the ViewModelStore collision, the sync data-loss race, the backend crash-on-any-error, the wizard crash) were only found this way, not by reading code.
- Every GitHub push and every Render redeploy in this project has needed explicit owner action (PAT generation, clicking Manual Deploy) — there is no CI/CD. Budget for that friction in any plan.
