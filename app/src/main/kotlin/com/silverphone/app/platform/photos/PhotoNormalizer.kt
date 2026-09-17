package com.silverphone.app.platform.photos

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.domain.NormalizedPhoto
import com.silverphone.app.domain.Sha256
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns any source image into the single stored form: a square JPEG no larger
 * than 512 px per side and 128 KiB, with no original metadata.
 *
 * The order of operations matters and is deliberate:
 *
 *  1. read the bytes with a hard cap, so a huge file cannot exhaust memory;
 *  2. read only the dimensions first, so a 24 MP photo is never decoded whole;
 *  3. sample down before decoding;
 *  4. apply EXIF orientation, then centre-crop to a square;
 *  5. encode with a fixed fallback ladder and keep the first result under the
 *     byte limit, never upscaling a small original.
 */
class PhotoNormalizer(context: Context) {

    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    sealed interface Result {
        /** Ready to be stored. */
        class Normalized(val photo: NormalizedPhoto) : Result

        data class Failed(val reason: Reason) : Result
    }

    enum class Reason {
        /** The source could not be opened, or the provider failed partway. */
        UNREADABLE,

        /** Larger than the input cap. */
        TOO_LARGE,

        /** Not JPEG, PNG or static WebP, or not decodable at all. */
        UNSUPPORTED_FORMAT,

        /** Decodable but could not be brought under the stored limits. */
        ENCODE_FAILED,
    }

    /** Dimensions of an encoded image, read without decoding its pixels. */
    class SourceDimensions(val width: Int, val height: Int) {
        val isSquare: Boolean get() = width == height
    }

    /**
     * Reads an image's dimensions without decoding it.
     *
     * Used by the archive reader, which has to decide whether a stored photo is
     * already in the shape the protocol requires *before* normalising: the
     * normaliser centre-crops, which would quietly turn a malformed archive into an
     * acceptable one.
     */
    suspend fun sourceDimensions(bytes: ByteArray): SourceDimensions? =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                SourceDimensions(bounds.outWidth, bounds.outHeight)
            }
        }

    suspend fun normalizeFromUri(uri: Uri): Result = withContext(Dispatchers.IO) {
        val bytes = try {
            readBounded(uri, ContactLimits.MAX_SOURCE_PHOTO_BYTES)
        } catch (tooLarge: FileTooLarge) {
            return@withContext Result.Failed(Reason.TOO_LARGE)
        } catch (failure: IOException) {
            return@withContext Result.Failed(Reason.UNREADABLE)
        } catch (failure: SecurityException) {
            return@withContext Result.Failed(Reason.UNREADABLE)
        }
        normalizeBytes(bytes)
    }

    /**
     * Normalises already-read bytes. Used for archive photos, where the bytes are
     * verified against the manifest before they get here.
     */
    suspend fun normalizeBytes(bytes: ByteArray): Result = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext Result.Failed(Reason.UNSUPPORTED_FORMAT)
        }
        if (!isSupportedMimeType(bounds.outMimeType)) {
            return@withContext Result.Failed(Reason.UNSUPPORTED_FORMAT)
        }
        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (pixels > ContactLimits.MAX_SOURCE_PHOTO_PIXELS) {
            return@withContext Result.Failed(Reason.TOO_LARGE)
        }

        val decoded = decodeSampled(bytes, bounds.outWidth, bounds.outHeight)
            ?: return@withContext Result.Failed(Reason.UNSUPPORTED_FORMAT)

        val oriented = try {
            applyExifOrientation(decoded, bytes)
        } catch (failure: Exception) {
            decoded
        }

        val squared = centerSquare(oriented)
        if (squared !== oriented) oriented.recycle()

        val photo = encodeToStoredJpeg(squared)
        squared.recycle()

        if (photo == null) Result.Failed(Reason.ENCODE_FAILED) else Result.Normalized(photo)
    }

    /**
     * Encodes an already square-cropped bitmap (the output of the crop screen).
     * The crop screen is responsible for the framing; this only enforces the
     * stored size limits.
     */
    suspend fun normalizeCroppedBitmap(bitmap: Bitmap): Result = withContext(Dispatchers.IO) {
        val squared = centerSquare(bitmap)
        // Only what this call created is recycled. centerSquare hands back the bitmap it
        // was given whenever that bitmap is already square - or whenever it could not
        // allocate - and the caller is the crop view, which may still be drawing the
        // bitmap it handed over. Recycling it here was a canvas-on-a-recycled-bitmap
        // crash waiting for the right timing.
        val owned = squared !== bitmap
        val photo = encodeToStoredJpeg(squared)
        if (owned) squared.recycle()
        if (photo == null) Result.Failed(Reason.ENCODE_FAILED) else Result.Normalized(photo)
    }

    // ------------------------------------------------------------- internals

    private class FileTooLarge : IOException("source exceeds the read cap")

    private fun readBounded(uri: Uri, maxBytes: Long): ByteArray {
        val stream = contentResolver.openInputStream(uri)
            ?: throw IOException("provider returned no stream for $uri")
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw FileTooLarge()
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun isSupportedMimeType(mimeType: String?): Boolean {
        val normalized = mimeType?.lowercase() ?: return false
        // Static WebP only; animated WebP is rejected rather than silently
        // imported as a single frame.
        return normalized == "image/jpeg" ||
            normalized == "image/png" ||
            normalized == "image/webp"
    }

    private fun decodeSampled(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        // Sample on the SHORTER edge, because what survives is a centre crop to a
        // square. Sizing on the longer edge throws away resolution the crop would
        // have kept: a 4000x1000 photo was stored at 250 px instead of 512 px.
        val shortestEdge = minOf(width, height)
        val longestEdge = maxOf(width, height)
        val target = ContactLimits.MAX_PHOTO_EDGE_PX * 2

        var sampleSize = 1
        while (shortestEdge / sampleSize > target) {
            sampleSize *= 2
        }
        // Sampling on the shorter edge can leave a very large decode for a long,
        // thin photo, so the total pixel count is bounded too.
        while ((longestEdge / sampleSize).toLong() * (shortestEdge / sampleSize) >
            MAX_DECODE_PIXELS
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun applyExifOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (failure: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return applyOrientation(bitmap, orientation)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }

            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
            )
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (failure: OutOfMemoryError) {
            bitmap
        }
    }

    /** Centre-crops to a square, scaling down to the stored edge but never up. */
    private fun centerSquare(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val cropped = try {
            if (side == source.width && side == source.height) {
                source
            } else {
                val left = (source.width - side) / 2
                val top = (source.height - side) / 2
                Bitmap.createBitmap(source, left, top, side, side)
            }
        } catch (failure: OutOfMemoryError) {
            return source
        }

        val edge = ContactLimits.MAX_PHOTO_EDGE_PX
        if (cropped.width <= edge) return cropped

        val scaled = try {
            Bitmap.createScaledBitmap(cropped, edge, edge, true)
        } catch (failure: OutOfMemoryError) {
            return cropped
        }
        if (scaled !== cropped && cropped !== source) cropped.recycle()
        return scaled
    }

    /**
     * Tries the documented quality/size ladder and returns the first result that
     * fits, or null if even the smallest does not. Never loops indefinitely and
     * never upscales.
     */
    private fun encodeToStoredJpeg(bitmap: Bitmap): NormalizedPhoto? {
        for (attempt in ATTEMPTS) {
            val candidate = if (bitmap.width > attempt.edge) {
                try {
                    Bitmap.createScaledBitmap(bitmap, attempt.edge, attempt.edge, true)
                } catch (failure: OutOfMemoryError) {
                    continue
                }
            } else {
                null
            }
            val source = candidate ?: bitmap
            try {
                val bytes = compress(source, attempt.quality) ?: continue
                if (bytes.size <= ContactLimits.MAX_PHOTO_BYTES) {
                    return NormalizedPhoto(
                        jpegBytes = bytes,
                        sha256 = Sha256.hex(bytes),
                        width = source.width,
                        height = source.height,
                    )
                }
            } finally {
                if (candidate != null && candidate !== bitmap) candidate.recycle()
            }
        }
        return null
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val ok = try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        } catch (failure: Exception) {
            return null
        }
        return if (ok) output.toByteArray() else null
    }

    private class Attempt(val edge: Int, val quality: Int)

    private companion object {
        /** Ceiling on one decoded bitmap, so a long thin photo cannot blow up the heap. */
        const val MAX_DECODE_PIXELS = 4_000_000L

        val ATTEMPTS = arrayOf(
            Attempt(512, 82),
            Attempt(512, 70),
            Attempt(384, 70),
            Attempt(256, 70),
        )
    }
}
