package com.hurricanes92.freedomiptv.security

import android.content.Context
import android.util.Base64
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
    fun savePassword(password: String) { prefs.edit().putString(PASSWORD_KEY, password).apply() }
    fun clearPassword() { prefs.edit().remove(PASSWORD_KEY).apply() }

    companion object { private const val PASSWORD_KEY = "xtream_password" }
}
