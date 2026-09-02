/**
 * Shared request-authentication for the Express API in index.js. Every REST endpoint requires a
 * valid Firebase ID token (verified here, never trusted from the client) — see DR RRP step 6:
 * "don't trust a patientId passed in the body without checking it against the token."
 */
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

/** Express middleware: verifies the bearer token, loads the caller's users/{uid} doc, attaches
 *  req.uid / req.role / req.linkedPatientId / req.canLogEntries. 401s if missing/invalid. */
async function authenticate(req, res, next) {
  const header = req.get("Authorization") || "";
  const match = header.match(/^Bearer (.+)$/);
  if (!match) return res.status(401).json({ error: "Missing bearer token." });

  let decoded;
  try {
    decoded = await getAuth().verifyIdToken(match[1]);
  } catch (e) {
    return res.status(401).json({ error: "Invalid or expired token." });
  }

  const userDoc = await getFirestore().collection("users").doc(decoded.uid).get();
  if (!userDoc.exists) return res.status(403).json({ error: "No account record for this user." });

  const data = userDoc.data();
  req.uid = decoded.uid;
  req.role = data.role;
  req.linkedPatientId = data.linkedPatientId || null;
  // Undefined/missing defaults to true — matches the client's "any linked caregiver can log
  // entries unless explicitly disabled" default (see step 5).
  req.canLogEntries = data.canLogEntries !== false;
  next();
}

/** True if the authenticated caller (already run through [authenticate]) may read/write patientId's data. */
function canAccessPatient(req, patientId) {
  if (req.role === "STAFF") return true;
  if (req.role === "PATIENT") return req.uid === patientId;
  if (req.role === "CAREGIVER") return req.linkedPatientId === patientId;
  return false;
}

/** Sends 403 and returns false if the caller can't access patientId; otherwise returns true. */
function requirePatientAccess(req, res, patientId) {
  if (canAccessPatient(req, patientId)) return true;
  res.status(403).json({ error: "You don't have access to this patient's data." });
  return false;
}

module.exports = { authenticate, canAccessPatient, requirePatientAccess };
