package net.myjda.payvi.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import net.myjda.payvi.data.prefs.PayviPrefs
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Downloads a payment screenshot and runs on-device ML Kit text
 * recognition on it. Nothing here calls out to any third-party OCR
 * service - the image never leaves the phone except to be downloaded from
 * the store itself.
 */
class ScreenshotOcrProcessor(private val prefs: PayviPrefs) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Downloads the screenshot and returns the raw OCR'd text, or null if
     * the image couldn't be downloaded/decoded at all (a genuine OCR
     * "nothing recognized" result still returns an empty/blank string, not
     * null - null specifically means "couldn't even load the image"). */
    suspend fun extractText(publicUrl: String, proxyUrl: String?): String? {
        val bitmap = downloadBitmap(publicUrl) ?: proxyUrl?.let { downloadBitmapWithAuth(it) }
        ?: return null

        return try {
            recognizeText(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (cont.isActive) cont.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        if (url.isBlank()) return null
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadBitmapWithAuth(url: String): Bitmap? {
        if (url.isBlank()) return null
        val key = prefs.apiKey ?: return null
        val secret = prefs.apiSecret ?: return null
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", Credentials.basic(key, secret))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            null
        }
    }
}
