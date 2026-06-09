package com.nimbleflux.glucosesync.shared.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Credentials(
    val username: String,
    val password: String,
    val baseUrl: String
)

class CredentialStore(context: Context) {
    private val appContext = context.applicationContext

    private val masterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "credentials_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private object Keys {
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val BASE_URL = "base_url"
        const val UID = "uid"
        const val REALNAME = "realname"
        const val SELECTED_PROVIDER = "selected_provider"
        const val SESSION_DISPLAY_NAME = "session_display_name"
        const val LIBRE_TOKEN = "libre_token"
        const val LIBRE_TOKEN_EXPIRES = "libre_token_expires"
        const val LIBRE_USER_ID = "libre_user_id"
        const val LIBRE_ACCOUNT_ID = "libre_account_id"
        const val LIBRE_REGION = "libre_region"
        const val LIBRE_PATIENT_ID = "libre_patient_id"
        const val LIBRE_PATIENT_NAME = "libre_patient_name"
    }

    suspend fun saveCredentials(creds: Credentials) {
        prefs.edit()
            .putString(Keys.USERNAME, creds.username)
            .putString(Keys.PASSWORD, creds.password)
            .putString(Keys.BASE_URL, creds.baseUrl)
            .apply()
    }

    suspend fun getCredentials(): Credentials? {
        val username = prefs.getString(Keys.USERNAME, null) ?: return null
        val password = prefs.getString(Keys.PASSWORD, null) ?: return null
        val baseUrl = prefs.getString(Keys.BASE_URL, "https://easyview.medtrum.eu") ?: "https://easyview.medtrum.eu"
        return Credentials(username, password, baseUrl)
    }

    suspend fun saveSession(uid: Long, realname: String) {
        prefs.edit()
            .putLong(Keys.UID, uid)
            .putString(Keys.REALNAME, realname)
            .apply()
    }

    suspend fun getSession(): Pair<Long, String>? {
        val uid = prefs.getLong(Keys.UID, 0L)
        if (uid == 0L) return null
        val realname = prefs.getString(Keys.REALNAME, "") ?: ""
        return uid to realname
    }

    suspend fun saveSelectedProvider(providerId: String) {
        prefs.edit().putString(Keys.SELECTED_PROVIDER, providerId).apply()
    }

    suspend fun getSelectedProvider(): String? {
        return prefs.getString(Keys.SELECTED_PROVIDER, null)
    }

    suspend fun saveSessionDisplayName(name: String) {
        prefs.edit().putString(Keys.SESSION_DISPLAY_NAME, name).apply()
    }

    suspend fun getSessionDisplayName(): String? {
        return prefs.getString(Keys.SESSION_DISPLAY_NAME, null)
    }

    suspend fun saveLibreSession(token: String, expires: Long, userId: String, accountId: String, regionUrl: String) {
        prefs.edit()
            .putString(Keys.LIBRE_TOKEN, token)
            .putLong(Keys.LIBRE_TOKEN_EXPIRES, expires)
            .putString(Keys.LIBRE_USER_ID, userId)
            .putString(Keys.LIBRE_ACCOUNT_ID, accountId)
            .putString(Keys.LIBRE_REGION, regionUrl)
            .apply()
    }

    suspend fun getLibreToken(): String? = prefs.getString(Keys.LIBRE_TOKEN, null)
    suspend fun getLibreTokenExpires(): Long = prefs.getLong(Keys.LIBRE_TOKEN_EXPIRES, 0L)
    suspend fun getLibreUserId(): String? = prefs.getString(Keys.LIBRE_USER_ID, null)
    suspend fun getLibreAccountId(): String? = prefs.getString(Keys.LIBRE_ACCOUNT_ID, null)
    suspend fun getLibreRegion(): String? = prefs.getString(Keys.LIBRE_REGION, null)
    suspend fun getLibrePatientId(): String? = prefs.getString(Keys.LIBRE_PATIENT_ID, null)
    suspend fun getLibrePatientName(): String? = prefs.getString(Keys.LIBRE_PATIENT_NAME, null)

    suspend fun saveLibrePatient(patientId: String) {
        prefs.edit().putString(Keys.LIBRE_PATIENT_ID, patientId).apply()
    }

    suspend fun saveLibrePatientName(name: String) {
        prefs.edit().putString(Keys.LIBRE_PATIENT_NAME, name).apply()
    }

    suspend fun clear() {
        prefs.edit().clear().apply()
    }
}
