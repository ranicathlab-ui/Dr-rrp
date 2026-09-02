# DR RRP — App Overview

Post-PCI (angioplasty) recovery monitoring app for **Aasai Health Centre, Salem**, under **Dr. A. Rajaram Prasad**. Patients (and their caregivers) log daily vitals/symptoms after a PCI procedure from home; clinic staff monitor everyone from a dashboard and get automatically flagged when a reading is out of range. Android only, package `com.postpci.drrrp`.

## Who uses it — three roles, one login screen

| Role | Gets in via | What they see |
|---|---|---|
| **Patient** | Staff-issued invite (temp password → sets their own on first login) | Their own Today / Trends / Alerts / Profile |
| **Caregiver** | Staff-issued invite, linked to one patient | The same four tabs, for their linked patient — entries they log are flagged `loggedByCaregiver` |
| **Staff** | Provisioned directly (not self-signup) | Clinic dashboard — patient list, baseline wizard, patient detail, messaging, add-caregiver |

Firebase Auth (email/password) is the identity layer; a `users/{uid}` Firestore doc carries the role and (for caregivers) which patient they're linked to. See `FIREBASE_INTEGRATION.md` for the backend wiring.

## Patient / Caregiver experience

Bottom-nav shell with four tabs (`PatientCaregiverShell.kt`):

- **Today** — the core loop. Greeting + "Day N post-PCI" badge, a banner for any unreviewed routine alerts ("N reading(s) need attention" → Contact Dr. Rajaram Prasad), a grid of today's *due* fields (see monitoring schedule below), and a medication checklist with DAPT visually highlighted. Tapping a due field opens a **Log Entry** bottom sheet — one focused field at a time, not one giant form — with inline validation and an immediate inline flag if the value is out of range, before it's even saved. For a caregiver whose account has logging disabled (`canLogEntries: false`), this whole screen is read-only instead.
- **Trends** — one line chart per metric (blood pressure — systolic + diastolic overlaid, heart rate, weight, SpO2), each with its normal-range band shaded, independently scrollable.
- **Alerts** — full chronological history of every flagged reading/event, colour-coded by severity, "Mark as reviewed" per item.
- **Profile** — read-only view of the full baseline the clinic recorded (demographics, procedural details, discharge labs & vitals, medications & follow-up, social) plus a prominent emergency-contact card. Editing is staff-only.
- **Messages** — single chat thread per patient with the clinic, reachable via an inbox icon on Today.

**Emergency escalation:** watched app-wide (not just on Today) by `EmergencyGateViewModel` — any unreviewed EMERGENCY-tier alert takes over the *entire* screen (hides the bottom nav too), with a single full-width primary action, **"Contact Dr. Rajaram Prasad"** (dials 9894184664 — same number as every other contact action in the app; there's no separate emergency-services number), until dismissed. This interrupts whichever tab the user is on.

## Staff experience

Own in-app navigation stack (`StaffShell.kt`, not the top-level nav graph):

- **Clinic Dashboard** — every patient, searchable by name, filterable by alert status (All/Emergency/Routine/None), sorted by most recent flag (unflagged patients sort last). Each card shows day-post-PCI, age, alert status, and a "missed entry" flag (due fields today but no entry logged since yesterday).
- **Baseline (onboarding) wizard** — 5 steps: *Demographics → Procedural → Labs & Vitals → Medications & Follow-up → Social*. Saves to Room on every "Next," so staff can back out mid-wizard and resume later from wherever they left off. Step 0 (Demographics) is also where a brand-new patient's invite gets created — the temp email/password is shown right there to hand over.
- **Patient detail** — baseline summary + paginated daily-log history (15 entries/page, never loads the whole history at once), "Call patient," "Send message," "Edit baseline," and (new this round) **"Add caregiver."**
- **Add caregiver** — name + contact in, creates the invite via the backend, shows the temp email/password to hand over — the caregiver counterpart of the patient-invite step in the wizard.
- **Messaging** — same thread UI as the patient side, from the staff perspective.

## Clinical monitoring logic

### Monitoring schedule (`MonitoringSchedule.kt`)

Each field has its own cadence — daily for a window after PCI, then either tapers to twice-weekly (Mon/Thu) or stops. This table drives which cards actually appear on Today each day:

| Field | Daily until day | After that |
|---|---|---|
| Chest pain | 28 | Twice-weekly |
| Breathlessness (NYHA) | 28 | Twice-weekly *(spec)* |
| Resting heart rate | 28 | Twice-weekly |
| Blood pressure | 14 | Twice-weekly *(spec)* |
| Weight | 28 | Twice-weekly |
| SpO2 | 28 | Twice-weekly *(spec)* |
| Access-site check | 7 | Stops entirely *(spec)* |
| Medications | always | always |
| Activity | 28 | Twice-weekly |
| Palpitations / syncope | always | always |

### Alert rules (`AlertRules.kt`) — two tiers

**Routine** → banner + dashboard flag + push notification, "Contact Dr. Rajaram Prasad":
- Resting HR outside 50–90 bpm
- Systolic BP <90 or >180 mmHg
- SpO2 <94%
- Weight gain >2 kg over 3 days
- Any access-site symptom (bleeding/swelling/pain/discolouration)
- DAPT dose not marked taken
- Exertional chest pain
- Palpitations alone
- NYHA class II–III breathlessness

**Emergency** → full-screen takeover, "Contact Dr. Rajaram Prasad" as the single primary action:
- Chest pain **at rest**
- Syncope or near-syncope
- A bleeding event that needed medical attention
- NYHA class IV (breathless at rest)

A field only ever produces one alert per check; a re-triggering of the same field/source while an alert is still unreviewed doesn't duplicate it.

## Data & architecture

- **Local-first**: Room database (`drrrp-encrypted.db`), **encrypted at rest via SQLCipher** — the passphrase lives in Android Keystore-backed `EncryptedSharedPreferences`, never in code. Every write updates the UI immediately; sync to the backend happens after.
- **Entities**: `PatientBaselineEntity` (five embedded sections mirroring the wizard steps), `DailyEntryEntity`, `BleedingEventEntity` (event-based, not daily-cadence), `AlertEntity`, `MessageEntity`.
- **Offline sync queue**: every entity carries a client-generated UUID and a `SyncStatus` (PENDING/SYNCED/FAILED). `SyncManager` drains all five queues to the REST backend, and pulls the patient's own data back down afterward; a client-generated ID makes retries (and pulls) safe — upsert, last-write-wins per record. Triggered two ways: a `WorkManager` periodic job every 15 minutes (`NetworkType.CONNECTED` — WorkManager itself waits for connectivity, no polling), and an immediate one-off request fired right after any local write. Screens also pull on open (staff dashboard, patient detail, Today, messaging).
- **REST contract** (`SyncApiService.kt`): `auth/register-device`, `patient/baseline` (create/edit), `patient/daily/{id}`, `patient/bleeding-event/{id}`, `patient/{id}` (paginated, includes alerts), `staff/patients`, `alert/acknowledge/{id}`, `message/{id}` (send + fetch). **Live** — implemented by `server/index.js`, a standalone Express server deployed to Render (`https://dr-rrp-aasai-backend.onrender.com`), not Firebase Cloud Functions (this project has no Blaze billing plan — see `FIREBASE_INTEGRATION.md` for the full story). Every endpoint verifies the Firebase ID token server-side and checks it against the patient being accessed.
- **Auth**: Firebase Auth (email/password) + a `users/{uid}` Firestore doc for role/`linkedPatientId`/`canLogEntries`. REST calls carry the Firebase ID token (`AuthInterceptor`) for the backend to verify.
- **Push**: FCM fully wired both ways — client receives (channels, token registration, notification handling) and the Render backend sends, inline when a new alert or message is created and daily for missed entries.

## Tech stack

Kotlin + Jetpack Compose (Material 3), min SDK 26 / target & compile SDK 37, Room 2.8 + SQLCipher, WorkManager, Retrofit + OkHttp + kotlinx.serialization, Firebase (Auth/Firestore/Messaging), a standalone Node/Express backend on Render, no DI framework (manual `DrRrpApplication`-as-container), no top-level design system beyond a shared dark theme (`ui/theme`) and hand-rolled components in `ui/common`.

## What's real vs. staged/fake right now

| Piece | Status |
|---|---|
| Local storage, offline queue, alert rules, monitoring schedule | ✅ fully real |
| Firebase Auth (sign-in, first-login, sign-out, roles) | ✅ real, live |
| Firestore rules | ✅ deployed |
| Backend (invites, REST sync, server-side alerts) | ✅ **live** on Render |
| FCM push, both directions | ✅ live |
| `canLogEntries` enforcement | ✅ live, client + server |

See `FIREBASE_INTEGRATION.md` for the detailed file-by-file breakdown and the Cloud Functions → Render pivot story.

## Known gaps

- **No STAFF account exists yet** in the live project — someone has to bootstrap the very first one manually (Firebase Console: Auth user + `users/{uid}` doc with `role: "STAFF"`), since invite creation itself requires an existing staff caller.
- Render free tier sleeps after ~15 min idle — first request after that is slow (30–50s), not broken.
- The daily missed-entry cron ping (cron-job.org) isn't set up yet — the endpoint exists and is protected, just nothing is calling it on a schedule yet.
- No automated UI/instrumented tests (would need a connected device/emulator); unit tests cover `AlertRules`, `MonitoringSchedule`, `EmergencyGateViewModel`, and `ClinicContact`.
