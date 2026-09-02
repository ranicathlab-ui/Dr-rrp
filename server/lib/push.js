/**
 * FCM send-side — the counterpart to the client's already-wired receive side
 * (DrRrpMessagingService). Sends data-only messages so the client's own channel/priority logic
 * in onMessageReceived always runs, even when the app is backgrounded. Payload keys (title, body,
 * severity) match exactly what DrRrpMessagingService reads.
 */
const { getMessaging } = require("firebase-admin/messaging");
const { getFirestore } = require("firebase-admin/firestore");

/** All FCM tokens registered for a set of uids (users/{uid}.fcmTokens), deduped. */
async function tokensForUids(uids) {
  const uniqueUids = [...new Set(uids)].filter(Boolean);
  if (uniqueUids.length === 0) return [];
  const firestore = getFirestore();
  const docs = await Promise.all(uniqueUids.map((uid) => firestore.collection("users").doc(uid).get()));
  const tokens = [];
  for (const doc of docs) {
    if (doc.exists && Array.isArray(doc.data().fcmTokens)) tokens.push(...doc.data().fcmTokens);
  }
  return [...new Set(tokens)];
}

/** uids of every STAFF account. */
async function staffUids() {
  const snap = await getFirestore().collection("users").where("role", "==", "STAFF").get();
  return snap.docs.map((d) => d.id);
}

/** uid of the patient + any caregiver(s) linked to them. */
async function patientAndCaregiverUids(patientId) {
  const caregiversSnap = await getFirestore().collection("users").where("linkedPatientId", "==", patientId).get();
  return [patientId, ...caregiversSnap.docs.map((d) => d.id)];
}

/** Sends a data-only push to a list of tokens. Best-effort — a send failure never throws back to
 *  the caller (it's a side effect of an otherwise-successful write, not something that should
 *  fail the request); invalid tokens are pruned from the owning user's doc. */
async function sendToTokens(tokens, data) {
  if (tokens.length === 0) return;
  try {
    const response = await getMessaging().sendEachForMulticast({
      tokens,
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
    });
    const stale = response.responses
      .map((r, i) => (!r.success && isUnregistered(r.error) ? tokens[i] : null))
      .filter(Boolean);
    if (stale.length > 0) await pruneTokens(stale);
  } catch (e) {
    console.error("FCM send failed", e);
  }
}

function isUnregistered(error) {
  return error && (error.code === "messaging/registration-token-not-registered" || error.code === "messaging/invalid-registration-token");
}

async function pruneTokens(staleTokens) {
  const { FieldValue } = require("firebase-admin/firestore");
  const firestore = getFirestore();
  const snap = await firestore.collection("users").where("fcmTokens", "array-contains-any", staleTokens.slice(0, 10)).get();
  await Promise.all(snap.docs.map((d) => d.ref.update({ fcmTokens: FieldValue.arrayRemove(...staleTokens) })));
}

module.exports = { tokensForUids, staffUids, patientAndCaregiverUids, sendToTokens };
