package com.postpci.drrrp.data.model

/** Firebase-auth-trusted role. Stored in Firestore alongside the Firebase user (see Stage 3). */
enum class UserRole { PATIENT, CAREGIVER, STAFF }

enum class Sex { MALE, FEMALE, OTHER }

enum class PreferredLanguage { TAMIL, ENGLISH, OTHER }

enum class SmartphoneLiteracy { LOW, MEDIUM, HIGH }

enum class SmokingStatus { NEVER, FORMER, CURRENT }

// --- Procedural ---

enum class StemiTerritory { ANTERIOR, INFERIOR, LATERAL, RV_INVOLVEMENT }

enum class CulpritVessel { LAD, LCX, RCA, LEFT_MAIN, GRAFT, OTHER }

/** TIMI flow grade, 0 (no flow) to 3 (normal flow), recorded pre- and post-PCI. */
enum class TimiFlow { GRADE_0, GRADE_1, GRADE_2, GRADE_3 }

enum class ThrombusBurden { NONE, LOW, MODERATE, HIGH }

enum class AccessSite { RADIAL, FEMORAL }

// --- Labs & vitals ---

enum class KillipClass { I, II, III, IV }

// --- Daily entries ---

enum class ChestPainType { REST, EXERTIONAL }

/** NYHA breathlessness class, I (no limitation) to IV (symptoms at rest). */
enum class NyhaClass { I, II, III, IV }

enum class BleedingSeverity { MINOR, MODERATE, MAJOR }

/** Distinguishes a routine out-of-range flag from a genuine emergency escalation, and a
 * missed-entry flag from either — see Alert Logic in the product spec. */
enum class AlertSeverity { INFO, ROUTINE, EMERGENCY }

enum class AlertSourceType { DAILY_ENTRY, BLEEDING_EVENT, MISSED_ENTRY }

/** Offline-first sync state for a locally-created record. */
enum class SyncStatus { PENDING, SYNCED, FAILED }
