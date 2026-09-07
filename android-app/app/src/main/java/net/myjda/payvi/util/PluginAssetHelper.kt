package net.myjda.payvi.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Handles getting the bundled payvi-connector.zip (the WordPress plugin)
 * out of app assets and either shared via another app or saved to the
 * device's Downloads folder, so the user can get it onto their WordPress
 * site.
 */
object PluginAssetHelper {

    private const val ASSET_NAME = "payvi-connector.zip"

    /** Copies the bundled plugin zip into app-private storage (if not
     * already there) and returns a content:// Uri suitable for sharing. */
    fun getShareableUri(context: Context): Uri {
        val outDir = File(context.filesDir, "payvi").apply { mkdirs() }
        val outFile = File(outDir, ASSET_NAME)

        if (!outFile.exists() || outFile.length() == 0L) {
            context.assets.open(ASSET_NAME).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
    }

    fun buildShareIntent(context: Context): Intent {
        val uri = getShareableUri(context)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Saves the plugin zip to the public Downloads folder. Requires
     * API 29+ (MediaStore.Downloads); returns false if unsupported or if
     * the write fails, in which case the caller should fall back to
     * suggesting Share instead. */
    fun saveToDownloads(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, ASSET_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false

            resolver.openOutputStream(uri)?.use { output ->
                context.assets.open(ASSET_NAME).use { input ->
                    input.copyTo(output)
                }
            } ?: return false

            true
        } catch (e: Exception) {
            false
        }
    }
}
