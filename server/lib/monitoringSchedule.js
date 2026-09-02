/**
 * Server-side port of app/src/main/java/com/postpci/drrrp/data/schedule/MonitoringSchedule.kt —
 * kept in lockstep with that file. Used for the /staff/patients "missed entry" flag and the
 * daily missed-entry scheduled check (see index.js).
 */

const NO_TAPER = Infinity;

const RULES = [
  { fieldKey: "chestPain", dailyUntilDay: 28, frequencyAfter: "TWICE_WEEKLY" },
  { fieldKey: "nyhaClass", dailyUntilDay: 28, frequencyAfter: "TWICE_WEEKLY" },
  { fieldKey: "restingHeartRate", dailyUntilDay: 28, frequencyAfter: "TWICE_WEEKLY" },
  { fieldKey: "bloodPressure", dailyUntilDay: 14, frequencyAfter: "TWICE_WEEKLY" },
  { fieldKey: "weight", dailyUntilDay: 28, frequencyAfter: "TWICE_WEEKLY" },
  { fieldKey: "spo2", dailyUntilDay: 28, frequencyAfter: "TWICE_WEEKLY" },
  { fieldKey: "accessSiteCheck", dailyUntilDay: 7, frequencyAfter: "NONE" },
  { fieldKey: "medicationsTaken", dailyUntilDay: NO_TAPER, frequencyAfter: "DAILY" },
  { fieldKey: "activity", dailyUntilDay: 28, frequencyAfter: "TWICE_WEEKLY" },
  { fieldKey: "palpitationsSyncope", dailyUntilDay: NO_TAPER, frequencyAfter: "DAILY" },
];

/** Days between two 'YYYY-MM-DD' (or Date) values, pciDate as day 0. */
function daysPostPci(pciDate, today) {
  const start = new Date(pciDate + "T00:00:00Z");
  const end = new Date(today + "T00:00:00Z");
  return Math.round((end - start) / 86400000);
}

function isDue(fieldKey, pciDate, today) {
  const rule = RULES.find((r) => r.fieldKey === fieldKey);
  if (!rule) return false;
  const dayN = daysPostPci(pciDate, today);
  if (dayN < 0) return false;
  if (dayN <= rule.dailyUntilDay) return true;
  switch (rule.frequencyAfter) {
    case "DAILY":
      return true;
    case "TWICE_WEEKLY": {
      const dow = new Date(today + "T00:00:00Z").getUTCDay(); // 0=Sun..6=Sat
      return dow === 1 || dow === 4; // Monday or Thursday
    }
    case "NONE":
    default:
      return false;
  }
}

function dueFieldsFor(pciDate, today) {
  return RULES.filter((r) => isDue(r.fieldKey, pciDate, today)).map((r) => r.fieldKey);
}

module.exports = { daysPostPci, isDue, dueFieldsFor };
