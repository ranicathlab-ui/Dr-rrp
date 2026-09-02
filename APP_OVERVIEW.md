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

- **Today** — the core loop. Greeting + "Day N post-PCI" badge, a banner for any unreviewed routine alerts ("N reading(s) need attention" → Call the clinic), a grid of today's *due* fields (see monitoring schedule below), and a medication checklist with DAPT visually highlighted. Tapping a due field opens a **Log Entry** bottom sheet — one focused field at a time, not one giant form — with inline validation and an immediate inline flag if the value is out of range, before it's even saved.
- **Trends** — one line chart per metric (blood pressure — systolic + diastolic overlaid, heart rate, weight, SpO2), each with its normal-range band shaded, independently scrollable.
- **Alerts** — full chronological history of every flagged reading/event, colour-coded by severity, "Mark as reviewed" per item.
- **Profile** — read-only view of the full baseline the clinic recorded (demographics, procedural details, discharge labs & vitals, medications & follow-up, social) plus a prominent emergency-contact card. Editing is staff-only.
- **Messages** — single chat thread per patient with the clinic, reachable via an inbox icon on Today.

**Emergency escalation:** watched app-wide (not just on Today) by `EmergencyGateViewModel` — any unreviewed EMERGENCY-tier alert takes over the *entire* screen (hides the bottom nav too) with "Call 108" as the primary action and "Call the clinic" secondary, until dismissed. This interrupts whichever tab the user is on.

## Staff experience

Own in-app navigation stack (`StaffShell.kt`, not the top-level nav graph):

- **Clinic Dashboard** — every patient, searchable by name, filterable by alert status (All/Emergency/Routine/None), sorted by most recent flag (unflagged patients sort last). Each card shows day-post-PCI, age, alert status, and a "missed entry" flag (due fields today but no entry logged since yesterday).
- **Baseline (onboarding) wizard** — 5 steps: *Demographics → Procedural → Labs & Vitals → Medications & Follow-up → Social*. Saves to Room on every "Next," so staff can back out mid-wizard and resume later from wherever they left off. Step 0 (Demographics) is also where a brand-new patient's invite gets created — the temp email/password is shown right there to hand over.
- **Patient detail** — baseline summary + paginated daily-log history (15 entries/page, never loads the whole history at once), "Call patient," "Send message," "Edit baseline," and (new this round) **"Add caregiver."**
- **Add caregiver** *(new)* — name + contact in, creates the invite via a Cloud Function, shows the temp email/password to hand over — the caregiver counterpart of the patient-invite step in the wizard.
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

**Routine** → banner + dashboard flag + (once a real backend exists) push notification, "Call the clinic":
- Resting HR outside 50–90 bpm
- Systolic BP <90 or >180 mmHg
- SpO2 <94%
- Weight gain >2 kg over 3 days
- Any access-site symptom (bleeding/swelling/pain/discolouration)
- DAPT dose not marked taken
- Exertional chest pain
- Palpitations alone
- NYHA class II–III breathlessness

**Emergency** → full-screen takeover, "Call 108" primary:
- Chest pain **at rest**
- Syncope or near-syncope
- A bleeding event that needed medical attention
- NYHA class IV (breathless at rest)

A field only ever produces one alert per check; a re-triggering of the same field/source while an alert is still unreviewed doesn't duplicate it.

## Data & architecture

- **Local-first**: Room database (`drrrp-encrypted.db`), **encrypted at rest via SQLCipher** — the passphrase lives in Android Keystore-backed `EncryptedSharedPreferences`, never in code. Every write updates the UI immediately; sync to the backend happens after.
- **Entities**: `PatientBaselineEntity` (five embedded sections mirroring the wizard steps), `DailyEntryEntity`, `BleedingEventEntity` (event-based, not daily-cadence), `AlertEntity`, `MessageEntity`.
- **Offline sync queue**: every entity carries a client-generated UUID and a `SyncStatus` (PENDING/SYNCED/FAILED). `SyncManager` drains all five queues to the REST backend; a client-generated ID makes retries safe (upsert, last-write-wins per record). Triggered two ways: a `WorkManager` periodic job every 15 minutes (`NetworkType.CONNECTED` — WorkManager itself waits for connectivity, no polling), and an immediate one-off request fired right after any local write.
- **REST contract** (`SyncApiService.kt`): `auth/register-device`, `patient/baseline` (create/edit), `patient/daily/{id}`, `patient/{id}` (paginated), `staff/patients`, `alert/acknowledge/{id}`, `message/{id}`. **No real backend exists yet** — `FakeSyncApiService` simulates success locally so the whole offline-queue → sync → SYNCED path is demonstrable end-to-end on-device.
- **Auth**: Firebase Auth (email/password) + a `users/{uid}` Firestore doc for role/`linkedPatientId`. REST calls carry the Firebase ID token (`AuthInterceptor`) for the backend to verify.
- **Push**: FCM wired client-side (channels, token registration, notification handling) — see `FIREBASE_INTEGRATION.md` for what's deployed vs. pending.

## Tech stack

Kotlin + Jetpack Compose (Material 3), min SDK 26 / target & compile SDK 37, Room 2.8 + SQLCipher, WorkManager, Retrofit + OkHttp + kotlinx.serialization, Firebase (Auth/Firestore/Functions/Messaging), no DI framework (manual `DrRrpApplication`-as-container), no top-level design system beyond a shared dark theme (`ui/theme`) and hand-rolled components in `ui/common`.

## What's real vs. staged/fake right now

| Piece | Status |
|---|---|
| Local storage, offline queue, alert rules, monitoring schedule | ✅ fully real |
| Firebase Auth (sign-in, first-login, sign-out, roles) | ✅ real, live |
| Firestore rules (`users/{uid}`) | ✅ deployed |
| Invite creation (Cloud Functions) | 🟡 written, not deployed (needs Blaze billing) |
| FCM push | 🟡 client wired, nothing sends real pushes yet (no backend) |
| REST backend (patient data sync) | ⛔ `FakeSyncApiService` only — no real server |

See `FIREBASE_INTEGRATION.md` for the detailed file-by-file breakdown of the Firebase work and exact next steps.

## Known gaps

- No real backend server for patient data (biggest remaining piece — separate project/stack decision).
- `ClinicContact.PHONE_NUMBER` is a placeholder (`+914270000000`) — the spec didn't give Aasai Health Centre's real number.
- No automated UI tests; unit tests cover `AlertRules` and `MonitoringSchedule` only.
- Caregiver's `canLogEntries` permission field exists in the Firestore schema but nothing reads/writes it yet — currently any linked caregiver can log entries.
