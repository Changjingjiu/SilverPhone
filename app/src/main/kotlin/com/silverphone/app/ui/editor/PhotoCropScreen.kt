package com.silverphone.app.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageView
import com.silverphone.app.R
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.ui.components.CancelActionButton
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * S06: confirm the square crop.
 *
 * The framing and gesture handling come from the maintained crop view; this
 * screen only supplies its own large buttons and copy. Saving the bitmap to
 * instance state is switched off, so no large image can end up in a saved-state
 * Bundle.
 */
@Composable
fun PhotoCropScreen(
    sourceUri: Uri,
    onUsePhoto: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var cropView by remember { mutableStateOf<CropImageView?>(null) }

    // While this step is on screen, system back means "cancel the crop". Without
    // it, back pops the whole editor destination and the name and number the
    // family just typed are lost.
    BackHandler(enabled = true) { onCancel() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(dimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
    ) {
        Text(
            text = stringResource(R.string.crop_title),
            style = styles.pageTitle,
            color = AppColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.crop_hint),
            style = styles.caption,
            color = AppColors.TextSecondary,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { viewContext ->
                    CropImageView(viewContext).apply {
                        // A square is the only stored shape, so the crop window is
                        // locked to one.
                        setFixedAspectRatio(true)
                        setAspectRatio(1, 1)
                        cropShape = CropImageView.CropShape.RECTANGLE
                        guidelines = CropImageView.Guidelines.ON
                        isAutoZoomEnabled = true
                        setMultiTouchEnabled(true)

                        // Kept deliberately despite being deprecated: the library
                        // defaults to storing the loaded bitmap in the view's saved
                        // instance state, and a large image must never travel through
                        // a Bundle. Removing this call would reintroduce that.
                        @Suppress("DEPRECATION")
                        isSaveBitmapToInstanceState = false

                        setOnSetImageUriCompleteListener { _, _, error ->
                            loading = false
                            failed = error != null
                        }
                        setOnCropImageCompleteListener { _, result ->
                            val bitmap = result.bitmap
                            if (result.isSuccessful && bitmap != null) {
                                onUsePhoto(bitmap)
                            } else {
                                loading = false
                                failed = true
                            }
                        }
                        setImageUriAsync(sourceUri)
                        cropView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (loading) {
                CircularProgressIndicator(color = AppColors.Ink)
                Text(
                    text = stringResource(R.string.crop_loading),
                    style = styles.body,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(top = 96.dp),
                )
            }
            if (failed) {
                Text(
                    text = stringResource(R.string.crop_failed),
                    style = styles.body,
                    color = AppColors.DangerRed,
                    modifier = Modifier.padding(horizontal = dimens.pagePadding),
                )
            }
        }

        if (failed) {
            Text(
                text = stringResource(R.string.crop_failed_hint),
                style = styles.caption,
                color = AppColors.TextSecondary,
            )
        }

        PrimaryActionButton(
            text = stringResource(R.string.crop_use),
            icon = Icons.Filled.Check,
            // Cropping before the image has loaded reports a failure and would then
            // look like a broken photo, so the action stays unavailable until there
            // is something to crop.
            enabled = !failed && !loading,
            onClick = {
                val view = cropView
                if (view == null) {
                    failed = true
                } else {
                    // Ask for a result no larger than the stored edge, at the
                    // highest quality; the normaliser enforces the byte limit.
                    view.croppedImageAsync(
                        Bitmap.CompressFormat.JPEG,
                        90,
                        ContactLimits.MAX_PHOTO_EDGE_PX,
                        ContactLimits.MAX_PHOTO_EDGE_PX,
                        CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                        null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        CancelActionButton(
            text = stringResource(R.string.crop_cancel),
            icon = Icons.Filled.Clear,
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
