package com.silverphone.app.platform.transfer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Moving a finished archive out of the app: saving it through the system file
 * picker, or offering it to the system share sheet.
 *
 * Everything here reports what actually happened. Opening the share sheet is not
 * delivery, and a save is only reported once the copy has been flushed and
 * closed.
 */
class ExportGateway(private val context: Context) {

    sealed interface CopyOutcome {
        data object Saved : CopyOutcome

        /** The copy failed and nothing usable was left at the target. */
        data class Failed(val cause: Throwable) : CopyOutcome

        /**
         * The copy failed and a partial file could not be removed. The family is
         * told, because a half-written archive looks like a valid one.
         */
        data class FailedWithLeftover(val cause: Throwable) : CopyOutcome
    }

    /**
     * Builds the share intent for a generated archive.
     *
     * The URI comes from our own FileProvider, which exposes only the exports
     * directory, and the read grant covers exactly this one URI.
     */
    fun buildShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = ZIP_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Required on newer versions so the receiving app can read the stream.
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        }
    }

    fun chooserFor(intent: Intent): Intent =
        Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /**
     * Copies the finished archive to a target the family chose.
     *
     * File providers do not promise atomic writes, so a failure may leave a
     * partial file behind. Only the document created by this call is ever
     * removed; anything else at that location is left untouched.
     */
    suspend fun copyTo(source: File, target: Uri): CopyOutcome = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(target)
                ?: return@withContext CopyOutcome.Failed(IOException("no output stream"))
            output.use { sink ->
                FileInputStream(source).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                    }
                }
                sink.flush()
            }
            CopyOutcome.Saved
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // A cancelled copy leaves a truncated archive at the target. Clean up
            // first, then propagate: a half-written file that looks valid is worse
            // than no file, because the family would send it to the other phone.
            deleteTarget(target)
            throw cancellation
        } catch (failure: Throwable) {
            if (deleteTarget(target)) {
                CopyOutcome.Failed(failure)
            } else {
                CopyOutcome.FailedWithLeftover(failure)
            }
        }
    }

    private fun deleteTarget(target: Uri): Boolean = try {
        context.contentResolver.delete(target, null, null) > 0
    } catch (failure: Exception) {
        false
    }

    companion object {
        const val ZIP_MIME_TYPE: String = "application/zip"
    }
}
