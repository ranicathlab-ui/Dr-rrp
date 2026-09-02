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

function generateTempPassword() {
  return crypto.randomBytes(5).toString("hex");
}

function syntheticEmail(name) {
  const slug = name.trim().toLowerCase().replace(/[^a-z0-9]+/g, ".").replace(/^\.+|\.+$/g, "");
  const suffix = crypto.randomBytes(2).toString("hex");
  return `${slug || "invite"}.${suffix}@${INVITE_EMAIL_DOMAIN}`;
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

    const latestSnap = await doc.ref.collection("entries").orderBy("entryDate", "desc").limit(1).get();
    const latestDate = latestSnap.empty ? null : latestSnap.docs[0].data().entryDate;
    if (latestDate && latestDate >= today) continue;

    const draft = { fieldKey: `missed:${dueToday.join(",")}`, severity: "INFO", message: "No entry logged today for one or more due readings.", normalRangeText: null };
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

  const email = syntheticEmail(name);
  const temporaryPassword = generateTempPassword();
  const userRecord = await getAuth().createUser({ email, password: temporaryPassword, displayName: name });

  await getFirestore().collection(USERS_COLLECTION).doc(userRecord.uid).set({
    role: "PATIENT",
    displayName: name,
    mustChangePassword: true,
    createdAt: new Date().toISOString(),
    createdBy: req.uid,
  });

  res.status(200).json({ patientId: userRecord.uid, email, temporaryPassword });
}));

app.post("/invite/caregiver", asyncHandler(async (req, res) => {
  if (req.role !== "STAFF") return res.status(403).json({ error: "Only staff can create invites." });
  const name = (req.body?.name || "").trim();
  const linkedPatientId = (req.body?.patientId || "").trim();
  const contactNumber = (req.body?.contactNumber || "").trim() || null;
  if (!name) return res.status(400).json({ error: "name is required." });
  if (!linkedPatientId) return res.status(400).json({ error: "patientId is required." });

  const email = syntheticEmail(name);
  const temporaryPassword = generateTempPassword();
  const userRecord = await getAuth().createUser({ email, password: temporaryPassword, displayName: name });

  await getFirestore().collection(USERS_COLLECTION).doc(userRecord.uid).set({
    role: "CAREGIVER",
    displayName: name,
    contactNumber,
    mustChangePassword: true,
    linkedPatientId,
    createdAt: new Date().toISOString(),
    createdBy: req.uid,
  });

  // Field is named patientId to match the Android InviteCredentials shape (it's actually the
  // new caregiver's own uid — see FakeAuthGateway.createCaregiverInvite for the same convention).
  res.status(200).json({ patientId: userRecord.uid, email, temporaryPassword });
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
          const latestSnap = await doc.ref.collection("entries").orderBy("entryDate", "desc").limit(1).get();
          const latestDate = latestSnap.empty ? null : latestSnap.docs[0].data().entryDate;
          hasMissedEntry = !latestDate || latestDate < today;
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

const port = process.env.PORT || 3000;
app.listen(port, () => console.log(`DR RRP backend listening on :${port}`));
