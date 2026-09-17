package com.silverphone.app.platform.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.silverphone.app.domain.ContactLimits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The stored-photo rules, checked on a device because the whole pipeline is
 * Android's image stack.
 *
 * The case worth guarding is a wide photo: the decoder used to sample on the
 * *longer* edge while the crop keeps the *shorter* one, so a 4000x1000 picture was
 * stored at 250 px instead of 512 px.
 */
@RunWith(AndroidJUnit4::class)
class PhotoNormalizerTest {

    private val normalizer = PhotoNormalizer(ApplicationProvider.getApplicationContext<Context>())

    private fun jpegOf(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // A gradient, so the encode is not trivially small and the content differs
        // across the frame.
        for (y in 0 until height step 8) {
            for (x in 0 until width step 8) {
                bitmap.setPixel(x, y, (x * 255 / width) shl 16 or (y * 255 / height) shl 8 or 0x40)
            }
        }
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private suspend fun normalize(width: Int, height: Int) =
        normalizer.normalizeBytes(jpegOf(width, height))

    @Test
    fun aWidePhotoIsStoredAtTheFullStoredEdge() = runTest {
        val result = normalize(4000, 1000)
        assertTrue("expected success, was $result", result is PhotoNormalizer.Result.Normalized)
        val photo = (result as PhotoNormalizer.Result.Normalized).photo
        assertEquals(photo.width, photo.height)
        assertEquals(ContactLimits.MAX_PHOTO_EDGE_PX, photo.width)
    }

    @Test
    fun anUltraWidePhotoIsStillStoredAtTheFullEdge() = runTest {
        val result = normalize(2560, 1080)
        val photo = (result as PhotoNormalizer.Result.Normalized).photo
        assertEquals(ContactLimits.MAX_PHOTO_EDGE_PX, photo.width)
    }

    @Test
    fun aTallPhotoIsAlsoStoredAtTheFullEdge() = runTest {
        val result = normalize(1000, 4000)
        val photo = (result as PhotoNormalizer.Result.Normalized).photo
        assertEquals(ContactLimits.MAX_PHOTO_EDGE_PX, photo.width)
    }

    @Test
    fun aSquarePhotoIsStoredAtTheFullEdge() = runTest {
        val result = normalize(2000, 2000)
        val photo = (result as PhotoNormalizer.Result.Normalized).photo
        assertEquals(ContactLimits.MAX_PHOTO_EDGE_PX, photo.width)
    }

    @Test
    fun aSmallPhotoIsNeverUpscaled() = runTest {
        val result = normalize(300, 200)
        val photo = (result as PhotoNormalizer.Result.Normalized).photo
        // Cropped to the shorter edge, and left at that size.
        assertEquals(200, photo.width)
    }

    @Test
    fun everyStoredPhotoIsWithinTheByteAndPixelLimits() = runTest {
        for ((width, height) in listOf(4000 to 1000, 2560 to 1080, 2000 to 2000, 600 to 400)) {
            val result = normalize(width, height)
            assertTrue("${width}x$height failed", result is PhotoNormalizer.Result.Normalized)
            val photo = (result as PhotoNormalizer.Result.Normalized).photo
            assertTrue(photo.byteCount in 1..ContactLimits.MAX_PHOTO_BYTES)
            assertTrue(photo.width <= ContactLimits.MAX_PHOTO_EDGE_PX)
            assertEquals(photo.width, photo.height)
            assertNotNull(BitmapFactory.decodeByteArray(photo.jpegBytes, 0, photo.byteCount))
        }
    }

    @Test
    fun aDamagedImageIsRejectedRatherThanStored() = runTest {
        val notAnImage = ByteArray(512) { 0x41 }
        val result = normalizer.normalizeBytes(notAnImage)
        assertTrue(result is PhotoNormalizer.Result.Failed)
    }
}
