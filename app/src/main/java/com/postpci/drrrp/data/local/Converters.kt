package com.postpci.drrrp.data.local

import androidx.room.TypeConverter
import com.postpci.drrrp.data.model.AccessSite
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.data.model.AlertSourceType
import com.postpci.drrrp.data.model.BleedingSeverity
import com.postpci.drrrp.data.model.ChestPainType
import com.postpci.drrrp.data.model.CulpritVessel
import com.postpci.drrrp.data.model.KillipClass
import com.postpci.drrrp.data.model.NyhaClass
import com.postpci.drrrp.data.model.PreferredLanguage
import com.postpci.drrrp.data.model.Sex
import com.postpci.drrrp.data.model.SmartphoneLiteracy
import com.postpci.drrrp.data.model.SmokingStatus
import com.postpci.drrrp.data.model.StemiTerritory
import com.postpci.drrrp.data.model.SyncStatus
import com.postpci.drrrp.data.model.ThrombusBurden
import com.postpci.drrrp.data.model.TimiFlow
import com.postpci.drrrp.data.model.UserRole
import java.time.LocalDate

/**
 * All conversions are `name <-> enum` or `epoch day <-> LocalDate`, so every converter below
 * follows the same two tiny patterns. java.time is used directly (no desugaring needed) since
 * minSdk 26 already ships it.
 */
class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun fromSex(value: Sex?): String? = value?.name

    @TypeConverter
    fun toSex(value: String?): Sex? = value?.let(Sex::valueOf)

    @TypeConverter
    fun fromPreferredLanguage(value: PreferredLanguage?): String? = value?.name

    @TypeConverter
    fun toPreferredLanguage(value: String?): PreferredLanguage? = value?.let(PreferredLanguage::valueOf)

    @TypeConverter
    fun fromSmartphoneLiteracy(value: SmartphoneLiteracy?): String? = value?.name

    @TypeConverter
    fun toSmartphoneLiteracy(value: String?): SmartphoneLiteracy? = value?.let(SmartphoneLiteracy::valueOf)

    @TypeConverter
    fun fromSmokingStatus(value: SmokingStatus?): String? = value?.name

    @TypeConverter
    fun toSmokingStatus(value: String?): SmokingStatus? = value?.let(SmokingStatus::valueOf)

    @TypeConverter
    fun fromStemiTerritory(value: StemiTerritory?): String? = value?.name

    @TypeConverter
    fun toStemiTerritory(value: String?): StemiTerritory? = value?.let(StemiTerritory::valueOf)

    @TypeConverter
    fun fromCulpritVessel(value: CulpritVessel?): String? = value?.name

    @TypeConverter
    fun toCulpritVessel(value: String?): CulpritVessel? = value?.let(CulpritVessel::valueOf)

    @TypeConverter
    fun fromTimiFlow(value: TimiFlow?): String? = value?.name

    @TypeConverter
    fun toTimiFlow(value: String?): TimiFlow? = value?.let(TimiFlow::valueOf)

    @TypeConverter
    fun fromThrombusBurden(value: ThrombusBurden?): String? = value?.name

    @TypeConverter
    fun toThrombusBurden(value: String?): ThrombusBurden? = value?.let(ThrombusBurden::valueOf)

    @TypeConverter
    fun fromAccessSite(value: AccessSite?): String? = value?.name

    @TypeConverter
    fun toAccessSite(value: String?): AccessSite? = value?.let(AccessSite::valueOf)

    @TypeConverter
    fun fromKillipClass(value: KillipClass?): String? = value?.name

    @TypeConverter
    fun toKillipClass(value: String?): KillipClass? = value?.let(KillipClass::valueOf)

    @TypeConverter
    fun fromChestPainType(value: ChestPainType?): String? = value?.name

    @TypeConverter
    fun toChestPainType(value: String?): ChestPainType? = value?.let(ChestPainType::valueOf)

    @TypeConverter
    fun fromNyhaClass(value: NyhaClass?): String? = value?.name

    @TypeConverter
    fun toNyhaClass(value: String?): NyhaClass? = value?.let(NyhaClass::valueOf)

    @TypeConverter
    fun fromBleedingSeverity(value: BleedingSeverity?): String? = value?.name

    @TypeConverter
    fun toBleedingSeverity(value: String?): BleedingSeverity? = value?.let(BleedingSeverity::valueOf)

    @TypeConverter
    fun fromAlertSeverity(value: AlertSeverity?): String? = value?.name

    @TypeConverter
    fun toAlertSeverity(value: String?): AlertSeverity? = value?.let(AlertSeverity::valueOf)

    @TypeConverter
    fun fromAlertSourceType(value: AlertSourceType?): String? = value?.name

    @TypeConverter
    fun toAlertSourceType(value: String?): AlertSourceType? = value?.let(AlertSourceType::valueOf)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus?): String? = value?.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? = value?.let(SyncStatus::valueOf)

    @TypeConverter
    fun fromUserRole(value: UserRole?): String? = value?.name

    @TypeConverter
    fun toUserRole(value: String?): UserRole? = value?.let(UserRole::valueOf)
}
