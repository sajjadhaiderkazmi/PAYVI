package net.myjda.payvi.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.myjda.payvi.data.model.PairingPayload

/**
 * Encrypted local storage for the store pairing credentials and simple app
 * settings. The pairing secret is sensitive (it's equivalent to a
 * password for this store's order data), so it's kept in
 * EncryptedSharedPreferences rather than plain SharedPreferences.
 */
class PayviPrefs private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val FILE_NAME = "payvi_secure_prefs"

        private const val KEY_SITE_URL = "site_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_SITE_NAME = "site_name"
        private const val KEY_LAST_SYNC_SINCE = "last_sync_since"
        private const val KEY_LAST_SYNC_AT_MILLIS = "last_sync_at_millis"
        private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        private const val KEY_SMS_LOOKBACK_DAYS = "sms_lookback_days"
        private const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"

        const val DEFAULT_SYNC_INTERVAL_MINUTES = 5
        const val DEFAULT_SMS_LOOKBACK_DAYS = 3

        @Volatile
        private var instance: PayviPrefs? = null

        fun getInstance(context: Context): PayviPrefs {
            return instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }
        }

        private fun create(context: Context): PayviPrefs {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return PayviPrefs(prefs)
        }
    }

    val isPaired: Boolean
        get() = !prefs.getString(KEY_SITE_URL, null).isNullOrBlank() &&
            !prefs.getString(KEY_API_KEY, null).isNullOrBlank() &&
            !prefs.getString(KEY_API_SECRET, null).isNullOrBlank()

    var siteUrl: String?
        get() = prefs.getString(KEY_SITE_URL, null)
        set(value) = prefs.edit().putString(KEY_SITE_URL, value).apply()

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var apiSecret: String?
        get() = prefs.getString(KEY_API_SECRET, null)
        set(value) = prefs.edit().putString(KEY_API_SECRET, value).apply()

    var siteName: String?
        get() = prefs.getString(KEY_SITE_NAME, null)
        set(value) = prefs.edit().putString(KEY_SITE_NAME, value).apply()

    /** Unix seconds cursor - only orders modified after this are re-fetched. */
    var lastSyncSince: Long
        get() = prefs.getLong(KEY_LAST_SYNC_SINCE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_SINCE, value).apply()

    var lastSyncAtMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT_MILLIS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT_MILLIS, value).apply()

    var syncIntervalMinutes: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL_MINUTES, DEFAULT_SYNC_INTERVAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_SYNC_INTERVAL_MINUTES, value).apply()

    var smsLookbackDays: Int
        get() = prefs.getInt(KEY_SMS_LOOKBACK_DAYS, DEFAULT_SMS_LOOKBACK_DAYS)
        set(value) = prefs.edit().putInt(KEY_SMS_LOOKBACK_DAYS, value).apply()

    var backgroundSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_SYNC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, value).apply()

    fun applyPairing(payload: PairingPayload) {
        prefs.edit()
            .putString(KEY_SITE_URL, payload.site)
            .putString(KEY_API_KEY, payload.key)
            .putString(KEY_API_SECRET, payload.secret)
            .putLong(KEY_LAST_SYNC_SINCE, 0L)
            .apply()
    }

    fun clearPairing() {
        prefs.edit()
            .remove(KEY_SITE_URL)
            .remove(KEY_API_KEY)
            .remove(KEY_API_SECRET)
            .remove(KEY_SITE_NAME)
            .remove(KEY_LAST_SYNC_SINCE)
            .remove(KEY_LAST_SYNC_AT_MILLIS)
            .apply()
    }
}
