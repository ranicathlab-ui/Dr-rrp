/**
 * NOT CURRENTLY USED — kept for reference only. Cloud Functions deployment requires the Blaze
 * billing plan, which isn't available for this project; the live backend is server/index.js
 * instead (a standalone Express server, same logic, deployed to Render — see server/README.md).
 * If Blaze ever becomes available, this file could be revived, but it isn't being kept in sync
 * with server/index.js going forward — treat server/ as the single source of truth.
 *
 * DR RRP Cloud Functions.
 *
 * The Firebase Auth *client* SDK can only create/modify the currently signed-in session, so a
 * signed-in staff member's device can't create a separate patient/caregiver's Auth account
 * directly (see AuthGateway.kt / FirebaseAuthGateway.kt in the Android app). These two callable
 * functions run with the Admin SDK instead, which can create any user's account, and are what
 * FirebaseAuthGateway.createPatientInvite/createCaregiverInvite call.
 *
 * Both functions:
 *   1. Require the caller to be signed in AND have role "STAFF" in their users/{uid} doc
 *      (checked server-side here — never trust a client-supplied role).
 *   2. Generate a synthetic, non-deliverable email (patients/caregivers may have no real email —
 *      see the "many patients are older and less tech-savvy" note elsewhere in the app) and a
 *      random temporary password.
 *   3. Create the Firebase Auth user, then the matching users/{uid} Firestore doc with
 *      mustChangePassword: true — FirebaseAuthGateway.signIn() checks that flag and routes the
 *      new user to NeedsPasswordSetup on their first sign-in with the temp password.
 *   4. Return { patientId, email, temporaryPassword } — the same InviteCredentials shape the
 *      Android client already expects from FakeAuthGateway.
 */

const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const crypto = require("crypto");
const express = require("express");

const { authenticate, requirePatientAccess } = require("./lib/auth");
const alertRules = require("./lib/alertRules");
const monitoringSchedule = require("./lib/monitoringSchedule");
const push = require("./lib/push");

initializeApp();

const USERS_COLLECTION = "users";
const INVITE_EMAIL_DOMAIN = "invite.drrrp.test"; // matches FakeAuthGateway's synthetic-email domain

function generateTempPassword() {
  // 10 hex chars — comparable entropy to the Kotlin fake's UUID.take(8), safely above Firebase
  // Auth's 6-character minimum.
  return crypto.randomBytes(5).toString("hex");
}

function syntheticEmail(name) {
  const slug = name.trim().toLowerCase().replace(/[^a-z0-9]+/g, ".").replace(/^\.+|\.+$/g, "");
  const suffix = crypto.randomBytes(2).toString("hex");
  return `${slug || "invite"}.${suffix}@${INVITE_EMAIL_DOMAIN}`;
}

/** Throws HttpsError if the caller isn't signed in with role STAFF in Firestore. */
async function requireStaff(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  const callerDoc = await getFirestore().collection(USERS_COLLECTION).doc(request.auth.uid).get();
  if (!callerDoc.exists || callerDoc.data().role !== "STAFF") {
    throw new HttpsError("permission-denied", "Only staff can create invites.");
  }
}

function requireNonEmptyString(value, field) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${field} is required.`);
  }
  return value.trim();
}

exports.createPatientInvite = onCall(async (request) => {
  await requireStaff(request);
  const name = requireNonEmptyString(request.data?.name, "name");

  const email = syntheticEmail(name);
  const temporaryPassword = generateTempPassword();

  const userRecord = await getAuth().createUser({
    email,
    password: temporaryPassword,
    displayName: name,
  });

  await getFirestore().collection(USERS_COLLECTION).doc(userRecord.uid).set({
    role: "PATIENT",
    displayName: name,
    mustChangePassword: true,
    createdAt: new Date().toISOString(),
    createdBy: request.auth.uid,
  });

  return { patientId: userRecord.uid, email, temporaryPassword };
});

exports.createCaregiverInvite = onCall(async (request) => {
  await requireStaff(request);
  const name = requireNonEmptyString(request.data?.name, "name");
  const linkedPatientId = requireNonEmptyString(request.data?.patientId, "patientId");

  const email = syntheticEmail(name);
  const temporaryPassword = generateTempPassword();

  const userRecord = await getAuth().createUser({
    email,
    password: temporaryPassword,
    displayName: name,
  });

  await getFirestore().collection(USERS_COLLECTION).doc(userRecord.uid).set({
    role: "CAREGIVER",
    displayName: name,
    mustChangePassword: true,
    linkedPatientId,
    createdAt: new Date().toISOString(),
    createdBy: request.auth.uid,
  });

  // Field is named patientId to match the Android InviteCredentials shape (it's actually the
  // new caregiver's own uid — see FakeAuthGateway.createCaregiverInvite for the same convention).
  return { patientId: userRecord.uid, email, temporaryPassword };
});

// ============================================================================================
// REST API — implements SyncApiService.kt's contract. One Express app behind one HTTPS function,
// fronted by a Firebase Hosting rewrite (see firebase.json) so RetrofitSyncApiService's plain
// path-based endpoints (POST patient/baseline, GET patient/{id}, ...) work unmodified against a
// single base URL. Every route requires a verified Firebase ID token (see lib/auth.js); no route
// trusts a patientId from the request body/path without checking it against the token first.
// ============================================================================================

const PATIENTS = "patients";
const PAGE_SIZE_DEFAULT = 20;

const app = express();
app.use(express.json({ limit: "1mb" }));
app.use(authenticate);

app.post("/auth/register-device", async (req, res) => {
  const token = req.body?.fcmToken;
  if (!token) return res.status(400).json({ error: "fcmToken is required." });
  await getFirestore().collection("users").doc(req.uid).set({ fcmTokens: FieldValue.arrayUnion(token) }, { merge: true });
  res.status(200).json({});
});

app.post("/patient/baseline", async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Baseline can only be created by staff." });
  const dto = req.body;
  if (!dto?.patientId) return res.status(400).json({ error: "patientId is required." });
  await getFirestore().collection(PATIENTS).doc(dto.patientId).set(dto, { merge: true });
  res.status(200).json({});
});

app.put("/patient/baseline/:patientId", async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Baseline can only be edited by staff." });
  await getFirestore().collection(PATIENTS).doc(req.params.patientId).set(req.body, { merge: true });
  res.status(200).json({});
});

app.post("/patient/daily/:patientId", async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;
  if (req.role === "CAREGIVER" && !req.canLogEntries) {
    return res.status(403).json({ error: "This caregiver account is read-only." });
  }
  const dto = req.body;
  if (!dto?.id) return res.status(400).json({ error: "id is required." });
  await getFirestore().collection(PATIENTS).doc(patientId).collection("entries").doc(dto.id).set(dto, { merge: true });
  res.status(200).json({});
});

// Not in the original REST contract — SyncManager.kt's own comment flagged this gap ("If a
// dedicated endpoint is added later, push it here"). Same access rules as a daily entry.
app.post("/patient/bleeding-event/:patientId", async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;
  if (req.role === "CAREGIVER" && !req.canLogEntries) {
    return res.status(403).json({ error: "This caregiver account is read-only." });
  }
  const dto = req.body;
  if (!dto?.id) return res.status(400).json({ error: "id is required." });
  await getFirestore().collection(PATIENTS).doc(patientId).collection("bleedingEvents").doc(dto.id).set(dto, { merge: true });
  res.status(200).json({});
});

app.get("/patient/:patientId", async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;

  const limit = Math.min(parseInt(req.query.limit, 10) || PAGE_SIZE_DEFAULT, 100);
  const offset = req.query.cursor ? parseInt(Buffer.from(String(req.query.cursor), "base64").toString("utf8"), 10) || 0 : 0;

  const firestore = getFirestore();
  const patientRef = firestore.collection(PATIENTS).doc(patientId);
  const [baselineSnap, entriesSnap, alertsSnap] = await Promise.all([
    patientRef.get(),
    patientRef.collection("entries").orderBy("entryDate", "desc").offset(offset).limit(limit).get(),
    // Alerts aren't paginated client-side (AlertsScreen shows the full history) — capped at 500
    // as a sanity bound rather than a real pagination scheme.
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
});

app.get("/staff/patients", async (req, res) => {
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
          const latestSnap = await doc.ref.collection("entries").orderBy("entryDate", "desc").limit(1).get();
          const latestDate = latestSnap.empty ? null : latestSnap.docs[0].data().entryDate;
          hasMissedEntry = !latestDate || latestDate < today;
        }
      }

      return {
        patientId: doc.id,
        name,
        age: p.demographics?.age ?? null,
        pciDate,
        lastAlertSeverity: p.lastAlertSeverity ?? null,
        lastAlertAt: p.lastAlertAt ?? null,
        hasMissedEntry,
      };
    }),
  );

  const filtered = items.filter(Boolean).sort((a, b) => (b.lastAlertAt || -1) - (a.lastAlertAt || -1));
  res.status(200).json(filtered);
});

app.post("/alert/acknowledge/:alertId", async (req, res) => {
  const firestore = getFirestore();
  const snap = await firestore.collectionGroup("alerts").where("id", "==", req.params.alertId).limit(1).get();
  if (snap.empty) return res.status(404).json({ error: "Alert not found." });

  const alertDoc = snap.docs[0];
  const patientId = alertDoc.data().patientId;
  if (!requirePatientAccess(req, res, patientId)) return;

  await alertDoc.ref.set(
    { reviewed: true, reviewedAt: Date.now(), reviewedByStaffId: req.body?.staffId ?? null },
    { merge: true },
  );
  res.status(200).json({});
});

app.post("/message/:patientId", async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;
  const dto = req.body;
  if (!dto?.id) return res.status(400).json({ error: "id is required." });
  await getFirestore().collection(PATIENTS).doc(patientId).collection("messages").doc(dto.id).set(dto, { merge: true });
  res.status(200).json({});
});

// Not in the original spec's endpoint list — needed so a message thread reaches a device other
// than the one that sent it (see SyncManager.pullMessages on the client).
app.get("/message/:patientId", async (req, res) => {
  const patientId = req.params.patientId;
  if (!requirePatientAccess(req, res, patientId)) return;
  const snap = await getFirestore().collection(PATIENTS).doc(patientId).collection("messages").orderBy("timestamp", "asc").get();
  res.status(200).json(snap.docs.map((d) => d.data()));
});

exports.api = onRequest(app);

// ============================================================================================
// Firestore triggers — server-side alert generation and push. The client still runs the same
// checks locally (data/alert/AlertRules.kt) for instant offline UI feedback; this is the
// authoritative copy every device converges on once synced. Alert docs use a deterministic id
// (`${sourceId}_${fieldKey}`) that exactly matches what PatientCareRepository.raiseAlert now
// writes client-side, so a pulled-down server alert just upserts onto the same local row instead
// of duplicating it — no separate reconciliation logic needed.
// ============================================================================================

async function createAlertIfAbsent(patientId, alertId, draft, sourceType, sourceId) {
  const firestore = getFirestore();
  const alertRef = firestore.collection(PATIENTS).doc(patientId).collection("alerts").doc(alertId);
  const existing = await alertRef.get();
  if (existing.exists) return false; // never overwrite — could clobber a staff review.

  await alertRef.set({
    id: alertId,
    patientId,
    sourceType,
    sourceId,
    fieldKey: draft.fieldKey,
    severity: draft.severity,
    message: draft.message,
    normalRangeText: draft.normalRangeText,
    createdAt: Date.now(),
    reviewed: false,
    reviewedAt: null,
    reviewedByStaffId: null,
  });

  await firestore.collection(PATIENTS).doc(patientId).set(
    { lastAlertSeverity: draft.severity, lastAlertAt: Date.now() },
    { merge: true },
  );

  return true;
}

async function pushForNewAlert(patientId, draft) {
  const patientCaregiverUids = await push.patientAndCaregiverUids(patientId);
  const recipientUids = draft.severity === "EMERGENCY" ? [...patientCaregiverUids, ...(await push.staffUids())] : patientCaregiverUids;
  const tokens = await push.tokensForUids(recipientUids);
  await push.sendToTokens(tokens, { title: draft.severity === "EMERGENCY" ? "Emergency alert" : "New alert", body: draft.message, severity: draft.severity });
}

exports.onDailyEntryWritten = onDocumentWritten("patients/{patientId}/entries/{entryId}", async (event) => {
  const entry = event.data?.after?.data();
  if (!entry) return; // deleted, nothing to check

  const { patientId, entryId } = event.params;
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
    const created = await createAlertIfAbsent(patientId, `${entryId}_${draft.fieldKey}`, draft, "DAILY_ENTRY", entryId);
    if (created) await pushForNewAlert(patientId, draft);
  }
});

exports.onBleedingEventWritten = onDocumentWritten("patients/{patientId}/bleedingEvents/{eventId}", async (event) => {
  const bleedingEvent = event.data?.after?.data();
  if (!bleedingEvent) return;

  const { patientId, eventId } = event.params;
  const draft = alertRules.checkBleedingEvent(bleedingEvent);
  const created = await createAlertIfAbsent(patientId, `${eventId}_bleedingEvent`, draft, "BLEEDING_EVENT", eventId);
  if (created) await pushForNewAlert(patientId, draft);
});

exports.onMessageWritten = onDocumentWritten("patients/{patientId}/messages/{messageId}", async (event) => {
  const message = event.data?.after?.data();
  const isNew = !event.data?.before?.exists;
  if (!message || !isNew) return;

  const { patientId } = event.params;
  const recipientUids = message.senderRole === "STAFF" ? await push.patientAndCaregiverUids(patientId) : await push.staffUids();
  const tokens = await push.tokensForUids(recipientUids);
  await push.sendToTokens(tokens, { title: `Message from ${message.senderName}`, body: message.text, severity: "INFO" });
});

/** Daily sweep for "due today but nothing logged since yesterday" — see AlertSourceType.MISSED_ENTRY
 *  and AlertSeverity.INFO in Enums.kt, both reserved for exactly this and unused until now. */
exports.checkMissedEntries = onSchedule({ schedule: "every day 18:00", timeZone: "Asia/Kolkata" }, async () => {
  const firestore = getFirestore();
  const today = new Date().toISOString().slice(0, 10);
  const patientsSnap = await firestore.collection(PATIENTS).get();

  for (const doc of patientsSnap.docs) {
    const p = doc.data();
    const pciDate = p.procedural?.pciDate;
    if (!pciDate) continue;

    const dueToday = monitoringSchedule.dueFieldsFor(pciDate, today);
    if (dueToday.length === 0) continue;

    const latestSnap = await doc.ref.collection("entries").orderBy("entryDate", "desc").limit(1).get();
    const latestDate = latestSnap.empty ? null : latestSnap.docs[0].data().entryDate;
    if (latestDate && latestDate >= today) continue; // logged today already

    const draft = {
      fieldKey: `missed:${dueToday.join(",")}`,
      severity: "INFO",
      message: "No entry logged today for one or more due readings.",
      normalRangeText: null,
    };
    const created = await createAlertIfAbsent(doc.id, `missed_${today}`, draft, "MISSED_ENTRY", null);
    if (created) await pushForNewAlert(doc.id, draft);
  }
});
