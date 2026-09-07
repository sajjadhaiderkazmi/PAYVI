package net.myjda.payvi.data.api

import net.myjda.payvi.BuildConfig
import net.myjda.payvi.data.prefs.PayviPrefs
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClientFactory {

    /** Builds an API client for an explicit site/key/secret - used during
     * pairing, before anything has been saved to [PayviPrefs] yet. */
    fun create(siteUrl: String, apiKey: String, apiSecret: String): PayviApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", Credentials.basic(apiKey, apiSecret))
                    .addHeader("X-PAYVI-Key", apiKey)
                    .addHeader("X-PAYVI-Secret", apiSecret)
                    .build()
                chain.proceed(request)
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        // BASIC (not BODY/HEADERS) so the api secret in our
                        // custom headers is never written to logcat.
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(siteUrl))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PayviApiService::class.java)
    }

    /** Builds an API client from whatever is currently saved in [PayviPrefs],
     * or null if the app isn't paired with a store yet. */
    fun createFromPrefs(prefs: PayviPrefs): PayviApiService? {
        val site = prefs.siteUrl ?: return null
        val key = prefs.apiKey ?: return null
        val secret = prefs.apiSecret ?: return null
        return create(site, key, secret)
    }

    private fun normalizeBaseUrl(siteUrl: String): String {
        val trimmed = siteUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
