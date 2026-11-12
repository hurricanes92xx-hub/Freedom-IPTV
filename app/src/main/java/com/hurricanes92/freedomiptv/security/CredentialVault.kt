package com.hurricanes92.freedomiptv.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keystore-backed encrypted storage for provider secrets. */
class CredentialVault(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "freedom_provider_secrets",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getPassword(): String? = prefs.getString(PASSWORD_KEY, null)
    /** commit() guarantees the encrypted value is durable before legacy plaintext is removed. */
    fun savePassword(password: String): Boolean = prefs.edit().putString(PASSWORD_KEY, password).commit()
    fun clearPassword(): Boolean = prefs.edit().remove(PASSWORD_KEY).commit()

    companion object { private const val PASSWORD_KEY = "xtream_password" }
}
