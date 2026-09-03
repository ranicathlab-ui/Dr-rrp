/**
 * DR RRP backend — standalone Express server, NOT a Google Cloud Function.
 *
 * Cloud Functions deployment requires a Firebase project on the Blaze plan (it builds your code
 * via Cloud Build and stores it in Artifact Registry — both GCP-wide paid APIs that need a
 * billing account attached before Google will even activate them, regardless of actual usage).
 * Since billing isn't an option here, every piece of privileged server-side logic this app needs
 * — invite creation, the REST sync API, server-side alert generation, FCM push — runs in this one
 * plain Node process instead, authenticating to Firebase via a service account key (free, no
 * billing required — see README.md for how to get one and where it goes).
 *
 * Firestore and Firebase Auth themselves stay exactly as they were: both are Spark (free-tier)
 * services with no billing requirement at all. Only the *compute* moved — this file is a drop-in
 * architectural swap for functions/index.js, not a different backend design. The Firestore
 * trigger logic from that version is inlined directly into the request handlers below instead
 * (this server already sees every write as it happens, so there's no need for a separate
 * event-trigger mechanism to react to them).
 */

const express = require("express");
const crypto = require("crypto");
const { initializeApp, cert } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const { authenticate, requirePatientAccess } = require("./lib/auth");
const alertRules = require("./lib/alertRules");
const monitoringSchedule = require("./lib/monitoringSchedule");
const push = require("./lib/push");

// ---- Firebase Admin init — service account credentials, not the Cloud Functions default ADC ----
// FIREBASE_SERVICE_ACCOUNT_JSON accepts either the raw JSON or a base64-encoded version of it.
// Some host UIs (Render's env var paste box included) detect pasted JSON and auto-split it into
// one variable per top-level key instead of keeping it as a single value — base64 has none of
// the characters ({, ", :, newlines) that trigger that, so it pastes as one clean value. See
// server/README.md for exactly how to generate it.
function loadServiceAccount() {
  const raw = (process.env.FIREBASE_SERVICE_ACCOUNT_JSON || "").trim();
  if (!raw) {
    console.error("FIREBASE_SERVICE_ACCOUNT_JSON is not set — see server/README.md. Exiting.");
    process.exit(1);
  }
  const jsonText = raw.startsWith("{") ? raw : Buffer.from(raw, "base64").toString("utf8");
  try {
    return JSON.parse(jsonText);
  } catch (e) {
    console.error("FIREBASE_SERVICE_ACCOUNT_JSON is set but isn't valid JSON (or valid base64-encoded JSON) — see server/README.md. Exiting.");
    process.exit(1);
  }
}
initializeApp({ credential: cert(loadServiceAccount()) });

const PATIENTS = "patients";
const USERS_COLLECTION = "users";
const PAGE_SIZE_DEFAULT = 20;
const INVITE_EMAIL_DOMAIN = "invite.drrrp.test"; // matches FakeAuthGateway's synthetic-email domain

function syntheticEmail(name) {
  const slug = name.trim().toLowerCase().replace(/[^a-z0-9]+/g, ".").replace(/^\.+|\.+$/g, "");
  const suffix = crypto.randomBytes(2).toString("hex");
  return `${slug || "invite"}.${suffix}@${INVITE_EMAIL_DOMAIN}`;
}

// Deliberately loose — this only guards against obvious typos before spending a Firebase Auth
// call on it; Firebase itself does the real validation and is the source of truth.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const MIN_PASSWORD_LENGTH = 6; // matches Firebase Auth's own minimum

/** Creates the Firebase Auth user for an invite, using [rawEmail] as the real login address when
 *  it's a plausible email, falling back to a synthetic `@invite.drrrp.test` one otherwise (see
 *  Demographics.email's doc for why that's a deliberate, not an error, path). [password] is
 *  staff-chosen (see AuthGateway.createPatientInvite's doc for why), not generated here. Surfaces
 *  Firebase's own "that email is already registered" error as a clean 409 instead of the generic
 *  500 the route would otherwise fall through to. */
async function createInviteUser(res, rawEmail, name, password) {
  if (!password || password.length < MIN_PASSWORD_LENGTH) {
    res.status(400).json({ error: `Password must be at least ${MIN_PASSWORD_LENGTH} characters.` });
    return null;
  }
  const trimmed = (rawEmail || "").trim();
  const usingRealEmail = trimmed.length > 0;
  if (usingRealEmail && !EMAIL_PATTERN.test(trimmed)) {
    res.status(400).json({ error: "That doesn't look like a valid email address." });
    return null;
  }
  const email = usingRealEmail ? trimmed : syntheticEmail(name);
  try {
    const userRecord = await getAuth().createUser({ email, password, displayName: name });
    return { userRecord, email };
  } catch (e) {
    if (e?.code === "auth/email-already-exists") {
      res.status(409).json({ error: `${email} is already registered — use a different email, or leave it blank.` });
      return null;
    }
    if (e?.code === "auth/invalid-email") {
      res.status(400).json({ error: "That doesn't look like a valid email address." });
      return null;
    }
    throw e;
  }
}

// Wraps an async route handler so a rejected promise reaches Express's error-handling middleware
// (below) instead of becoming an unhandled rejection. Express 4 does NOT do this automatically for
// async handlers — every route in this file used to be one uncaught Firestore/Auth error away from
// crashing the whole Node process, which Render then reports as 502 to every concurrent client
// until it restarts, and the exact same crash repeats the next time the same bad request is
// retried (which the Android client's offline-sync queue does automatically). Reproduced live via
// the old /alert/acknowledge/:alertId route's collectionGroup query, which needed a Firestore index
// that was never created — every retry of that one alert's acknowledgment took the whole backend
// down until it was rewritten below to not need cross-patient querying at all.
function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}

const app = express();
app.use(express.json({ limit: "1mb" }));

app.get("/health", (req, res) => res.status(200).json({ ok: true }));

const PRIVACY_POLICY_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Privacy Policy — DR RRP</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background-color: #0C1322; color: #E1E7EF; line-height: 1.6; margin: 0; padding: 24px 16px; }
    .container { max-width: 800px; margin: 0 auto; background-color: #141D30; padding: 32px; border-radius: 16px; border: 1px solid #2A364F; }
    h1 { color: #F5C518; margin-top: 0; font-size: 28px; }
    h2 { color: #F5C518; font-size: 20px; border-bottom: 1px solid #2A364F; padding-bottom: 8px; margin-top: 28px; }
    p, li { color: #C0CADA; font-size: 15px; }
    ul { padding-left: 20px; }
    .contact-box { background-color: #1C273E; border-left: 4px solid #F5C518; padding: 16px; border-radius: 8px; margin-top: 24px; }
    a { color: #F5C518; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Privacy Policy — DR RRP</h1>
    <p><strong>Aasai Health Centre, Salem, Tamil Nadu, India</strong><br>Effective Date: September 2026</p>

    <h2>1. Introduction</h2>
    <p>DR RRP ("the Application") is a cardiac post-procedure recovery monitoring system developed for Aasai Health Centre, Salem, India, under Dr. A. Rajaram Prasad. We are committed to protecting the privacy, confidentiality, and security of patient health data.</p>

    <h2>2. Information We Collect</h2>
    <p>The Application collects only the health and personal information required to facilitate post-PCI (angioplasty) recovery monitoring:</p>
    <ul>
      <li><strong>Patient Demographics:</strong> Name, age, sex, contact number, comorbidities, and home medications.</li>
      <li><strong>Clinical Baseline Data:</strong> Procedural details (PCI date, stent specifications, STEMI territory), laboratory values, discharge vitals, and medication regimens.</li>
      <li><strong>Daily Recovery Logs:</strong> Resting heart rate, blood pressure, SpO2, body weight, chest pain episodes, breathlessness levels, access-site status, activity levels, and medication adherence.</li>
      <li><strong>Communication & Chat Data:</strong> Direct in-app messages between patients/caregivers and clinic staff.</li>
    </ul>

    <h2>3. How We Use Your Information</h2>
    <p>Your information is used solely for clinical follow-up and patient care:</p>
    <ul>
      <li>To monitor post-procedure recovery and identify out-of-range vitals or symptoms requiring medical attention.</li>
      <li>To enable direct communication between patients, caregivers, and authorized cardiology staff at Aasai Health Centre.</li>
      <li>To send automated alert notifications regarding recovery milestones and vital checks.</li>
    </ul>

    <h2>4. Data Storage & Security</h2>
    <p>We enforce strict technical and organizational safeguards to protect health data:</p>
    <ul>
      <li>On-device data is encrypted at rest using AES-256 SQLCipher encryption.</li>
      <li>Data in transit is encrypted using Secure Sockets Layer / Transport Layer Security (TLS/HTTPS).</li>
      <li>Role-based access control ensures patient health data is accessible only by authorized clinical staff and linked caregivers.</li>
    </ul>

    <h2>5. Data Deletion & Patient Rights</h2>
    <p>Patients have the right to request complete deletion of their account and health data at any time. Data deletion requests can be initiated directly within the Profile section of the Application or by contacting Aasai Health Centre.</p>

    <div class="contact-box">
      <strong>Aasai Health Centre</strong><br>
      Salem, Tamil Nadu, India<br>
      <strong>Director:</strong> Dr. A. Rajaram Prasad
    </div>
  </div>
</body>
</html>`;

const TERMS_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Terms & Conditions — DR RRP</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background-color: #0C1322; color: #E1E7EF; line-height: 1.6; margin: 0; padding: 24px 16px; }
    .container { max-width: 800px; margin: 0 auto; background-color: #141D30; padding: 32px; border-radius: 16px; border: 1px solid #2A364F; }
    h1 { color: #F5C518; margin-top: 0; font-size: 28px; }
    h2 { color: #F5C518; font-size: 20px; border-bottom: 1px solid #2A364F; padding-bottom: 8px; margin-top: 28px; }
    p, li { color: #C0CADA; font-size: 15px; }
    ul { padding-left: 20px; }
    .alert-box { background-color: rgba(229, 57, 53, 0.15); border-left: 4px solid #E53935; padding: 16px; border-radius: 8px; margin-top: 20px; color: #FFCDD2; }
    .contact-box { background-color: #1C273E; border-left: 4px solid #F5C518; padding: 16px; border-radius: 8px; margin-top: 24px; }
    a { color: #F5C518; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Terms & Conditions — DR RRP</h1>
    <p><strong>Aasai Health Centre, Salem, Tamil Nadu, India</strong><br>Effective Date: September 2026</p>

    <h2>1. Acceptance of Terms</h2>
    <p>By registering or using the DR RRP application ("the Application"), you agree to comply with these Terms & Conditions. If you do not agree, please do not use the Application.</p>

    <h2>2. Medical Disclaimer — Not an Emergency Response System</h2>
    <div class="alert-box">
      <strong>CRITICAL NOTICE:</strong> DR RRP is a recovery monitoring and follow-up communication tool. It is NOT an automated real-time emergency dispatch or emergency triage system. If you experience severe chest pain, extreme breathlessness, or loss of consciousness, CALL EMERGENCY SERVICES (108) OR GO TO THE NEAREST HOSPITAL IMMEDIATELY.
    </div>

    <h2>3. Intended Use</h2>
    <p>The Application is designed exclusively for post-PCI (angioplasty) patients under the care of Aasai Health Centre, Salem. It facilitates routine recovery tracking, vital log entries, and non-emergency communication with clinical staff.</p>

    <h2>4. Account Confidentiality</h2>
    <p>Users are responsible for maintaining the confidentiality of their login credentials. Any activity conducted under a user's account is their responsibility.</p>

    <h2>5. Revisions to Terms</h2>
    <p>Aasai Health Centre reserves the right to update these terms to reflect medical, legal, or technological improvements. Continued use of the Application constitutes acceptance of revised terms.</p>

    <div class="contact-box">
      <strong>Aasai Health Centre</strong><br>
      Salem, Tamil Nadu, India<br>
      <strong>Director:</strong> Dr. A. Rajaram Prasad
    </div>
  </div>
</body>
</html>`;

app.get("/privacy-policy", (req, res) => {
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(PRIVACY_POLICY_HTML);
});

app.get("/terms", (req, res) => {
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(TERMS_HTML);
});

// Registered before authenticate: this is hit by an external cron service, not a signed-in
// Firebase user — it has its own shared-secret check instead (see the handler below).
app.post("/internal/check-missed-entries", asyncHandler(async (req, res) => {
  if (!process.env.MISSED_ENTRY_CRON_SECRET || req.get("X-Cron-Secret") !== process.env.MISSED_ENTRY_CRON_SECRET) {
    return res.status(401).json({ error: "Unauthorized." });
  }

  const firestore = getFirestore();
  const today = new Date().toISOString().slice(0, 10);
  const patientsSnap = await firestore.collection(PATIENTS).get();
  let flagged = 0;

  for (const doc of patientsSnap.docs) {
    const p = doc.data();
    const pciDate = p.procedural?.pciDate;
    if (!pciDate) continue;

    const dueToday = monitoringSchedule.dueFieldsFor(pciDate, today);
    if (dueToday.length === 0) continue;

    const entriesSnap = await doc.ref.collection("entries").where("entryDate", "==", today).get();
    if (!entriesSnap.empty) continue; // Patient already completed mandatory check-in (submission_count >= 1)

    const draft = { fieldKey: `missed:${dueToday.join(",")}`, severity: "INFO", message: "You haven’t completed your daily check-in yet. Please log your vitals today.", normalRangeText: null };
    await createAlertIfAbsentAndPush(doc.id, `missed_${today}`, draft, "MISSED_ENTRY", null);
    flagged++;
  }

  res.status(200).json({ checked: patientsSnap.docs.length, flagged });
}));

app.use(authenticate);

// ============================================================================================
// Invite creation — was createPatientInvite/createCaregiverInvite (callable Cloud Functions).
// Same logic, plain REST now: the Android client hits these via InviteApiService (see
// FirebaseAuthGateway.kt) instead of the Firebase Functions SDK.
// ============================================================================================

app.post("/invite/patient", asyncHandler(async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Only staff can create invites." });
  const name = (req.body?.name || "").trim();
  if (!name) return res.status(400).json({ error: "name is required." });

  const created = await createInviteUser(res, req.body?.email, name, req.body?.password);
  if (!created) return; // createInviteUser already sent the error response
  const { userRecord, email } = created;

  await getFirestore().collection(USERS_COLLECTION).doc(userRecord.uid).set({
    role: "PATIENT",
    displayName: name,
    mustChangePassword: true,
    createdAt: new Date().toISOString(),
    createdBy: req.uid,
  });

  res.status(200).json({ patientId: userRecord.uid, email, temporaryPassword: req.body?.password });
}));

app.post("/invite/staff", asyncHandler(async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Only staff can create staff accounts." });
  const name = (req.body?.name || "").trim();
  const password = (req.body?.password || "").trim();
  const email = (req.body?.email || "").trim();
  if (!name) return res.status(400).json({ error: "name is required." });
  if (!email) return res.status(400).json({ error: "email is required." });

  const created = await createInviteUser(res, email, name, password);
  if (!created) return;
  const { userRecord } = created;

  await getFirestore().collection(USERS_COLLECTION).doc(userRecord.uid).set({
    role: "STAFF",
    displayName: name,
    mustChangePassword: false,
    createdAt: new Date().toISOString(),
    createdBy: req.uid,
  });

  res.status(200).json({ staffId: userRecord.uid, email, temporaryPassword: password });
}));

app.post("/invite/caregiver", asyncHandler(async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Only staff can create invites." });
  const name = (req.body?.name || "").trim();
  const linkedPatientId = (req.body?.patientId || "").trim();
  const contactNumber = (req.body?.contactNumber || "").trim() || null;
  if (!name) return res.status(400).json({ error: "name is required." });
  if (!linkedPatientId) return res.status(400).json({ error: "patientId is required." });

  const created = await createInviteUser(res, req.body?.email, name, req.body?.password);
  if (!created) return; // createInviteUser already sent the error response
  const { userRecord, email } = created;

  await getFirestore().collection(USERS_COLLECTION).doc(userRecord.uid).set({
    role: "CAREGIVER",
    displayName: name,
    contactNumber,
    canLogEntries: true,
    mustChangePassword: true,
    linkedPatientId,
    createdAt: new Date().toISOString(),
    createdBy: req.uid,
  });

  // Field is named patientId to match the Android InviteCredentials shape (it's actually the
  // new caregiver's own uid — see FakeAuthGateway.createCaregiverInvite for the same convention).
  res.status(200).json({ patientId: userRecord.uid, email, temporaryPassword: req.body?.password });
}));

// ============================================================================================
// REST API — implements SyncApiService.kt's contract. Every route requires a verified Firebase
// ID token (lib/auth.js); no route trusts a patientId from the request body/path without
// checking it against the token first.
// ============================================================================================

app.post("/auth/register-device", asyncHandler(async (req, res) => {
  const token = req.body?.fcmToken;
  if (!token) return res.status(400).json({ error: "fcmToken is required." });
  await getFirestore().collection(USERS_COLLECTION).doc(req.uid).set({ fcmTokens: FieldValue.arrayUnion(token) }, { merge: true });
  res.status(200).json({});
}));

app.post("/patient/baseline", asyncHandler(async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Baseline can only be created by staff." });
  const dto = req.body;
  if (!dto?.patientId) return res.status(400).json({ error: "patientId is required." });
  await getFirestore().collection(PATIENTS).doc(dto.patientId).set(dto, { merge: true });
  res.status(200).json({});
}));

app.put("/patient/baseline/:patientId", asyncHandler(async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Baseline can only be edited by staff." });
  await getFirestore().collection(PATIENTS).doc(req.params.patientId).set(req.body, { merge: true });
  res.status(200).json({});
}));

/** Runs the alert checks for one freshly-written daily entry, writes any new alert docs
 *  (create-if-absent — never overwrites, could clobber a staff review), updates the parent
 *  patient doc's denormalized lastAlertSeverity/lastAlertAt, and pushes for each new one.
 *  Inlined here (and in the bleeding-event/message handlers below) rather than a separate
 *  Firestore trigger — this server already sees the write as it happens. */
async function generateAlertsForEntry(patientId, entryId, entry) {
  const firestore = getFirestore();
  let entriesLast3Days = [];
  if (entry.weightKg != null) {
    const today = entry.entryDate;
    const windowStart = new Date(new Date(today + "T00:00:00Z").getTime() - 3 * 86400000).toISOString().slice(0, 10);
    const recentSnap = await firestore
      .collection(PATIENTS).doc(patientId).collection("entries")
      .where("entryDate", ">=", windowStart).where("entryDate", "<", today)
      .get();
    entriesLast3Days = recentSnap.docs.map((d) => d.data());
  }
  const drafts = alertRules.checkEntry(entry, entriesLast3Days);
  for (const draft of drafts) {
    await createAlertIfAbsentAndPush(patientId, `${entryId}_${draft.fieldKey}`, draft, "DAILY_ENTRY", entryId);
  }
}

async function createAlertIfAbsentAndPush(patientId, alertId, draft, sourceType, sourceId) {
  const firestore = getFirestore();
  const alertRef = firestore.collection(PATIENTS).doc(patientId).collection("alerts").doc(alertId);
  const existing = await alertRef.get();
  if (existing.exists) return; // never overwrite — could clobber a staff review

  await alertRef.set({
    id: alertId, patientId, sourceType, sourceId, fieldKey: draft.fieldKey, severity: draft.severity,
    message: draft.message, normalRangeText: draft.normalRangeText, createdAt: Date.now(),
    reviewed: false, reviewedAt: null, reviewedByStaffId: null,
  });
  await firestore.collection(PATIENTS).doc(patientId).set({ lastAlertSeverity: draft.severity, lastAlertAt: Date.now() }, { merge: true });

  const patientCaregiverUids = await push.patientAndCaregiverUids(patientId);
  const recipientUids = draft.severity === "EMERGENCY" ? [...patientCaregiverUids, ...(await push.staffUids())] : patientCaregiverUids;
  await push.sendToTokens(await push.tokensForUids(recipientUids), {
    title: draft.severity === "EMERGENCY" ? "Emergency alert" : "New alert",
    body: draft.message,
    severity: draft.severity,
  });
}

app.post("/patient/daily/:patientId", asyncHandler(async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;
  if (req.role === "CAREGIVER" && !req.canLogEntries) return res.status(403).json({ error: "This caregiver account is read-only." });

  const dto = req.body;
  if (!dto?.id) return res.status(400).json({ error: "id is required." });
  await getFirestore().collection(PATIENTS).doc(patientId).collection("entries").doc(dto.id).set(dto, { merge: true });

  try {
    await generateAlertsForEntry(patientId, dto.id, dto);
  } catch (e) {
    console.error("alert generation failed for entry", dto.id, e); // the entry write above already succeeded; don't fail the request over this
  }
  res.status(200).json({});
}));

app.post("/patient/bleeding-event/:patientId", asyncHandler(async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;
  if (req.role === "CAREGIVER" && !req.canLogEntries) return res.status(403).json({ error: "This caregiver account is read-only." });

  const dto = req.body;
  if (!dto?.id) return res.status(400).json({ error: "id is required." });
  await getFirestore().collection(PATIENTS).doc(patientId).collection("bleedingEvents").doc(dto.id).set(dto, { merge: true });

  try {
    const draft = alertRules.checkBleedingEvent(dto);
    await createAlertIfAbsentAndPush(patientId, `${dto.id}_${draft.fieldKey}`, draft, "BLEEDING_EVENT", dto.id);
  } catch (e) {
    console.error("alert generation failed for bleeding event", dto.id, e);
  }
  res.status(200).json({});
}));

app.get("/patient/:patientId", asyncHandler(async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;

  const limit = Math.min(parseInt(req.query.limit, 10) || PAGE_SIZE_DEFAULT, 100);
  const offset = req.query.cursor ? parseInt(Buffer.from(String(req.query.cursor), "base64").toString("utf8"), 10) || 0 : 0;

  const firestore = getFirestore();
  const patientRef = firestore.collection(PATIENTS).doc(patientId);
  const [baselineSnap, entriesSnap, alertsSnap] = await Promise.all([
    patientRef.get(),
    patientRef.collection("entries").orderBy("entryDate", "desc").offset(offset).limit(limit).get(),
    patientRef.collection("alerts").orderBy("createdAt", "desc").limit(500).get(),
  ]);

  const dailyEntries = entriesSnap.docs.map((d) => d.data());
  const nextCursor = dailyEntries.length === limit ? Buffer.from(String(offset + limit)).toString("base64") : null;

  res.status(200).json({
    baseline: baselineSnap.exists ? baselineSnap.data() : null,
    dailyEntries,
    alerts: alertsSnap.docs.map((d) => d.data()),
    nextCursor,
  });
}));

app.delete("/patient/:patientId", asyncHandler(async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Only staff can delete patient records." });
  const patientId = req.params.patientId;
  const firestore = getFirestore();

  // 1. Delete patient document from 'patients' collection and subcollections
  const patientRef = firestore.collection(PATIENTS).doc(patientId);
  const subcollections = ["entries", "alerts", "bleedingEvents", "messages"];
  for (const sub of subcollections) {
    try {
      const snap = await patientRef.collection(sub).get();
      const batch = firestore.batch();
      snap.docs.forEach((doc) => batch.delete(doc.ref));
      if (!snap.empty) await batch.commit();
    } catch (_e) {}
  }
  await patientRef.delete();

  // 2. Invalidate patient login access & remove user document
  try {
    await getAuth().updateUser(patientId, { disabled: true });
  } catch (_e) {}
  try {
    await firestore.collection(USERS_COLLECTION).doc(patientId).delete();
  } catch (_e) {}

  // 3. Invalidate linked caregiver accounts
  try {
    const caregiversSnap = await firestore.collection(USERS_COLLECTION)
      .where("role", "==", "CAREGIVER")
      .where("linkedPatientId", "==", patientId)
      .get();
    for (const cDoc of caregiversSnap.docs) {
      try {
        await getAuth().updateUser(cDoc.id, { disabled: true });
      } catch (_e) {}
      await cDoc.ref.delete();
    }
  } catch (_e) {}

  res.status(200).json({ ok: true });
}));

app.get("/staff/patients", asyncHandler(async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Staff only." });
  const search = (req.query.search || "").toString().toLowerCase();

  const firestore = getFirestore();
  const patientsSnap = await firestore.collection(PATIENTS).get();
  const today = new Date().toISOString().slice(0, 10);

  const items = await Promise.all(
    patientsSnap.docs.map(async (doc) => {
      const p = doc.data();
      const name = p.demographics?.name || "";
      if (search && !name.toLowerCase().includes(search)) return null;

      const pciDate = p.procedural?.pciDate || null;
      let hasMissedEntry = false;
      if (pciDate) {
        const dueToday = monitoringSchedule.dueFieldsFor(pciDate, today);
        if (dueToday.length > 0) {
          const entriesSnap = await doc.ref.collection("entries").where("entryDate", "==", today).get();
          hasMissedEntry = entriesSnap.empty; // true if 0 submissions today
        }
      }

      return {
        patientId: doc.id, name, age: p.demographics?.age ?? null, pciDate,
        lastAlertSeverity: p.lastAlertSeverity ?? null, lastAlertAt: p.lastAlertAt ?? null, hasMissedEntry,
      };
    }),
  );

  res.status(200).json(items.filter(Boolean).sort((a, b) => (b.lastAlertAt || -1) - (a.lastAlertAt || -1)));
}));

app.get("/patient/:patientId/caregivers", asyncHandler(async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;

  const snap = await getFirestore().collection(USERS_COLLECTION).where("role", "==", "CAREGIVER").where("linkedPatientId", "==", patientId).get();
  res.status(200).json(
    snap.docs.map((d) => {
      const c = d.data();
      return { uid: d.id, displayName: c.displayName || "", contactNumber: c.contactNumber ?? null, canLogEntries: c.canLogEntries !== false };
    }),
  );
}));

app.put("/caregiver/:caregiverId/permissions", asyncHandler(async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Only staff can change caregiver permissions." });
  const caregiverId = req.params.caregiverId;
  const caregiverDoc = await getFirestore().collection(USERS_COLLECTION).doc(caregiverId).get();
  if (!caregiverDoc.exists || caregiverDoc.data()?.role !== "CAREGIVER") return res.status(404).json({ error: "Caregiver not found." });

  const canLogEntries = req.body?.canLogEntries !== false; // defaults true, same convention as the read side
  await caregiverDoc.ref.set({ canLogEntries }, { merge: true });
  res.status(200).json({});
}));

// :patientId is part of the path here, not a cross-patient collectionGroup().where("id", "==", ...)
// search: that query shape needs a manual collection-group index that was never created for this
// project, and every acknowledgment of a real alert was throwing FAILED_PRECONDITION as a result —
// which, unhandled by any route in this file until asyncHandler above, was crashing the whole
// backend process on every retry (reproduced live: a stuck pending alert-acknowledge in the
// Android client's offline-sync queue took the entire service down repeatedly). The client always
// has the alert's own patientId locally already (AlertEntity.patientId), so there's no reason to
// search for it server-side — a direct doc reference needs no index at all.
app.post("/alert/acknowledge/:patientId/:alertId", asyncHandler(async (req, res) => {
  const { patientId, alertId } = req.params;
  if (!requirePatientAccess(req, res, patientId)) return;

  const alertRef = getFirestore().collection(PATIENTS).doc(patientId).collection("alerts").doc(alertId);
  const alertDoc = await alertRef.get();
  if (!alertDoc.exists) return res.status(404).json({ error: "Alert not found." });

  await alertRef.set({ reviewed: true, reviewedAt: Date.now(), reviewedByStaffId: req.body?.staffId ?? null }, { merge: true });
  res.status(200).json({});
}));

app.post("/message/:patientId", asyncHandler(async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;
  const dto = req.body;
  if (!dto?.id) return res.status(400).json({ error: "id is required." });
  await getFirestore().collection(PATIENTS).doc(patientId).collection("messages").doc(dto.id).set(dto, { merge: true });

  try {
    const recipientUids = dto.senderRole === "STAFF" ? await push.patientAndCaregiverUids(patientId) : await push.staffUids();
    await push.sendToTokens(await push.tokensForUids(recipientUids), { title: `Message from ${dto.senderName}`, body: dto.text, severity: "INFO" });
  } catch (e) {
    console.error("push failed for message", dto.id, e);
  }
  res.status(200).json({});
}));

app.get("/message/:patientId", asyncHandler(async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;
  const snap = await getFirestore().collection(PATIENTS).doc(patientId).collection("messages").orderBy("timestamp", "asc").get();
  res.status(200).json(snap.docs.map((d) => d.data()));
}));

// authenticate() runs before every route above, including /invite/*, so an authentication
// failure there (missing/invalid token) already responded before reaching this handler — this
// only catches routes that don't match anything at all.
app.use((req, res) => res.status(404).json({ error: "Not found." }));

// Final safety net: catches whatever asyncHandler forwards via next(err), plus any synchronous
// throw from a non-async handler. Without this, an uncaught error here would fall through to
// Express's default handler, which sends an HTML stack trace instead of the JSON every Android
// client expects — and depending on Node/Express version, can still crash the process.
app.use((err, req, res, next) => {
  console.error("Unhandled request error:", err);
  if (res.headersSent) return next(err);
  res.status(500).json({ error: "Internal server error." });
});

// Belt-and-suspenders: log anything that still gets past every handler above instead of letting
// it silently crash the process (Node's default for an unhandled rejection since v15). This
// should never actually fire now that every route goes through asyncHandler, but a mistake here
// is exactly the kind of thing that took the whole backend down repeatedly before that existed.
process.on("unhandledRejection", (reason) => console.error("Unhandled promise rejection:", reason));

async function seedInitialStaffAccounts() {
  const staffList = [
    { email: "drprasad27@yahoo.co.in", name: "Dr. A. Rajaram Prasad", password: "drrrpapp@2026" },
    { email: "deepthibr@gmail.com", name: "Dr. Deepthi B R", password: "drrrpapp@2026" },
    { email: "dreswaran@gmail.com", name: "Dr. Eswaran", password: "drrrpapp@2026" },
  ];

  for (const s of staffList) {
    try {
      let userRecord;
      try {
        userRecord = await getAuth().getUserByEmail(s.email);
        console.log(`Staff user ${s.email} already exists in Firebase Auth (${userRecord.uid}).`);
      } catch (e) {
        if (e?.code === "auth/user-not-found") {
          userRecord = await getAuth().createUser({ email: s.email, password: s.password, displayName: s.name });
          console.log(`Created staff user ${s.email} in Firebase Auth (${userRecord.uid}).`);
        } else {
          throw e;
        }
      }

      await getFirestore().collection("users").doc(userRecord.uid).set({
        role: "STAFF",
        displayName: s.name,
        mustChangePassword: false,
        createdAt: new Date().toISOString(),
      }, { merge: true });
      console.log(`Seeded Firestore users/${userRecord.uid} role = STAFF for ${s.name}.`);
    } catch (err) {
      console.error(`Failed seeding staff ${s.email}:`, err);
    }
  }
}

const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log(`DR RRP backend listening on :${port}`);
  seedInitialStaffAccounts();
});
