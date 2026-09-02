package com.postpci.drrrp.data.sync.dto

import kotlinx.serialization.Serializable

/** Wire shape for `POST /patient/daily/{patientId}` — mirrors [com.postpci.drrrp.data.local.entity.DailyEntryEntity]. */
@Serializable
data class DailyEntryDto(
    val id: String,
    val patientId: String,
    val entryDate: String, // ISO-8601 (LocalDate.toString())
    val loggedByCaregiver: Boolean,
    val chestPainCount: Int? = null,
    val chestPainType: String? = null,
    val nyhaClass: String? = null,
    val restingHeartRate: Int? = null,
    val bpSystolic: Int? = null,
    val bpDiastolic: Int? = null,
    val weightKg: Double? = null,
    val spo2: Int? = null,
    val accessSiteBleeding: Boolean? = null,
    val accessSiteSwelling: Boolean? = null,
    val accessSitePain: Boolean? = null,
    val accessSiteDiscolouration: Boolean? = null,
    val medicationsTaken: String? = null,
    val daptTaken: Boolean? = null,
    val stepsOrMinutesWalked: Int? = null,
    val symptomThatStoppedActivity: String? = null,
    val palpitations: Boolean? = null,
    val syncope: Boolean? = null,
    val nearSyncope: Boolean? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Wire shape for bleeding events, sent alongside daily entries during sync. */
@Serializable
data class BleedingEventDto(
    val id: String,
    val patientId: String,
    val timestamp: Long,
    val site: String,
    val severity: String,
    val neededMedicalAttention: Boolean,
    val notes: String? = null,
    val loggedByCaregiver: Boolean,
)

@Serializable
data class AlertDto(
    val id: String,
    val patientId: String,
    val sourceType: String,
    val sourceId: String? = null,
    val fieldKey: String,
    val severity: String,
    val message: String,
    val normalRangeText: String? = null,
    val createdAt: Long,
    val reviewed: Boolean,
    val reviewedAt: Long? = null,
    val reviewedByStaffId: String? = null,
)

@Serializable
data class MessageDto(
    val id: String,
    val patientId: String,
    val senderRole: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
)

@Serializable
data class DeviceRegisterRequest(val fcmToken: String, val platform: String = "android")

@Serializable
data class AlertAcknowledgeRequest(val staffId: String? = null)

@Serializable
data class CaregiverDto(
    val uid: String,
    val displayName: String,
    val contactNumber: String? = null,
    val canLogEntries: Boolean = true,
)

@Serializable
data class SetCaregiverPermissionRequest(val canLogEntries: Boolean)
