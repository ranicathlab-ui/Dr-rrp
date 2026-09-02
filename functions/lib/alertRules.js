/**
 * Server-side port of app/src/main/java/com/postpci/drrrp/data/alert/AlertRules.kt — kept in
 * lockstep with that file. This is what "wire alert creation as a server-side trigger" (see
 * DR RRP step 6) actually runs: the client still computes the same checks locally for instant
 * offline UI feedback, but this copy is the authoritative one that produces the alert doc every
 * device eventually converges on.
 *
 * Each check returns { fieldKey, severity, message, normalRangeText } or null — same shape as
 * the Kotlin AlertDraft.
 */

function checkRestingHeartRate(bpm) {
  if (bpm >= 50 && bpm <= 90) return null;
  return { fieldKey: "restingHeartRate", severity: "ROUTINE", message: `Resting heart rate ${bpm} bpm is outside the expected range.`, normalRangeText: "50–90 bpm" };
}

function checkBloodPressure(systolic, diastolic) {
  if (systolic < 90 || systolic > 180) {
    return {
      fieldKey: "bpSystolic",
      severity: "ROUTINE",
      message: `Blood pressure ${systolic}/${diastolic} mmHg — systolic is outside the safe range.`,
      normalRangeText: "target <130/80; flag if systolic <90 or >180",
    };
  }
  return null;
}

function checkSpo2(percent) {
  if (percent >= 94) return null;
  return { fieldKey: "spo2", severity: "ROUTINE", message: `SpO2 ${percent}% is below the safe threshold.`, normalRangeText: "≥94%" };
}

/** entriesLast3Days: array of { weightKg, entryDate } for the 3 days before today (excluding today). */
function checkWeightGain(todayKg, entriesLast3Days) {
  const weights = entriesLast3Days.map((e) => e.weightKg).filter((w) => w != null);
  if (weights.length === 0) return null;
  const earliestInWindow = Math.min(...weights);
  const gain = todayKg - earliestInWindow;
  if (gain <= 2.0) return null;
  return {
    fieldKey: "weight",
    severity: "ROUTINE",
    message: `Weight gain of ${gain.toFixed(1)} kg over the last 3 days.`,
    normalRangeText: "flag if gain >2 kg over 3 days",
  };
}

function checkAccessSite(bleeding, swelling, pain, discolouration) {
  if (!bleeding && !swelling && !pain && !discolouration) return null;
  const symptoms = [
    bleeding ? "bleeding" : null,
    swelling ? "swelling" : null,
    pain ? "pain" : null,
    discolouration ? "discolouration" : null,
  ].filter(Boolean).join(", ");
  return { fieldKey: "accessSiteCheck", severity: "ROUTINE", message: `Access-site check flagged: ${symptoms}.`, normalRangeText: "no bleeding, swelling, pain, or discolouration" };
}

function checkDaptTaken(taken) {
  if (taken) return null;
  return { fieldKey: "medicationsTaken", severity: "ROUTINE", message: "DAPT dose not marked as taken today.", normalRangeText: "DAPT must be taken as prescribed" };
}

function checkChestPain(count, type) {
  if (!count || count <= 0) return null;
  if (type === "REST") {
    return { fieldKey: "chestPain", severity: "EMERGENCY", message: `Chest pain at rest reported (${count} episode(s)) — treated as an emergency.`, normalRangeText: "no chest pain at rest" };
  }
  return { fieldKey: "chestPain", severity: "ROUTINE", message: `Chest pain reported (${count} episode(s)).`, normalRangeText: "no chest pain" };
}

function checkSymptomFlags(palpitations, syncope, nearSyncope) {
  if (syncope) return { fieldKey: "syncope", severity: "EMERGENCY", message: "Syncope (fainting) reported — treated as an emergency.", normalRangeText: "no syncope" };
  if (nearSyncope) return { fieldKey: "syncope", severity: "EMERGENCY", message: "Near-syncope reported — treated as an emergency.", normalRangeText: "no near-syncope" };
  if (palpitations) return { fieldKey: "palpitations", severity: "ROUTINE", message: "Palpitations reported today.", normalRangeText: "no palpitations" };
  return null;
}

function checkBleedingEvent(event) {
  if (event.neededMedicalAttention) {
    return { fieldKey: "bleedingEvent", severity: "EMERGENCY", message: `Bleeding event at ${event.site} needed medical attention — treated as an emergency.`, normalRangeText: "no bleeding needing medical attention" };
  }
  return { fieldKey: "bleedingEvent", severity: "ROUTINE", message: `Bleeding event logged at ${event.site}.`, normalRangeText: "no bleeding events" };
}

function checkBreathlessness(nyha) {
  if (!nyha || nyha === "I") return null;
  if (nyha === "IV") {
    return { fieldKey: "nyhaClass", severity: "EMERGENCY", message: "NYHA Class IV breathlessness (symptoms at rest) — treated as an emergency.", normalRangeText: "NYHA Class I (no limitation)" };
  }
  return { fieldKey: "nyhaClass", severity: "ROUTINE", message: `Breathlessness reported today (NYHA Class ${nyha}).`, normalRangeText: "NYHA Class I (no limitation)" };
}

/** Runs every entry-level check against one daily-entry doc. Returns an array of drafts (possibly empty). */
function checkEntry(entry, entriesLast3Days) {
  const drafts = [];
  if (entry.restingHeartRate != null) drafts.push(checkRestingHeartRate(entry.restingHeartRate));
  if (entry.bpSystolic != null && entry.bpDiastolic != null) drafts.push(checkBloodPressure(entry.bpSystolic, entry.bpDiastolic));
  if (entry.spo2 != null) drafts.push(checkSpo2(entry.spo2));
  if (entry.weightKg != null) drafts.push(checkWeightGain(entry.weightKg, entriesLast3Days || []));
  if (entry.accessSiteBleeding != null || entry.accessSiteSwelling != null || entry.accessSitePain != null || entry.accessSiteDiscolouration != null) {
    drafts.push(checkAccessSite(!!entry.accessSiteBleeding, !!entry.accessSiteSwelling, !!entry.accessSitePain, !!entry.accessSiteDiscolouration));
  }
  if (entry.daptTaken != null) drafts.push(checkDaptTaken(entry.daptTaken));
  if (entry.chestPainCount != null) drafts.push(checkChestPain(entry.chestPainCount, entry.chestPainType));
  if (entry.palpitations != null || entry.syncope != null || entry.nearSyncope != null) {
    drafts.push(checkSymptomFlags(!!entry.palpitations, !!entry.syncope, !!entry.nearSyncope));
  }
  if (entry.nyhaClass != null) drafts.push(checkBreathlessness(entry.nyhaClass));
  return drafts.filter(Boolean);
}

module.exports = { checkEntry, checkBleedingEvent };
