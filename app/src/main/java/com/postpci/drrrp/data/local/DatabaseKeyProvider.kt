package com.postpci.drrrp.data.local

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Generates (once) and retrieves the SQLCipher database passphrase. The passphrase itself is
 * stored only inside Android Keystore-backed EncryptedSharedPreferences — never hardcoded, never
 * logged, never sent anywhere.
 */
object DatabaseKeyProvider {
    private const val PREFS_NAME = "drrrp_secure_prefs"
    private const val KEY_ALIAS = "db_passphrase"
    private const val KEY_BYTES = 32

    fun getOrCreateKey(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val existing = prefs.getString(KEY_ALIAS, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }

        val newKey = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_ALIAS, Base64.encodeToString(newKey, Base64.NO_WRAP)).apply()
        return newKey
    }
}
