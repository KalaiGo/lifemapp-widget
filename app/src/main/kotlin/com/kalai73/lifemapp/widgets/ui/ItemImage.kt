package com.kalai73.lifemapp.widgets.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons

/** An image item's picture, ready to draw, or why there is none. */
sealed interface ItemImage {
    data class Loaded(val bitmap: ImageBitmap) : ItemImage
    /** Encrypted, and this device isn't unlocked */
    data object Locked : ItemImage
    /** No picture, unreachable from this device, or the download failed */
    data object Failed : ItemImage
}

/** Longest side a picture is decoded at: enough for the full-screen view, without huge bitmaps in memory. */
private const val MAX_DECODE_PX = 2048

/** Decodes downloaded picture bytes, subsampled to [MAX_DECODE_PX]; null when they aren't an image. */
internal fun decodeItemImage(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

/**
 * An expanded image item's picture under its header, as on the web card: centered, at most 256dp tall, rounded
 * and bordered. A tap opens it full screen; a failed load is retried on tap.
 */
@Composable
internal fun ItemPicture(item: TopicItemUi, load: suspend (itemId: String) -> ItemImage) {
    val t = LocalTok.current
    var attempt by remember(item.id) { mutableIntStateOf(0) }
    var image by remember(item.id) { mutableStateOf<ItemImage?>(null) }
    var fullscreen by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id, attempt) {
        image = null
        image = load(item.id)
    }
    val shape = RoundedCornerShape(if (t.predator) 0.dp else 6.dp)
    Box(
        Modifier.fillMaxWidth().padding(start = 35.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = image) {
            null -> CircularProgressIndicator(Modifier.padding(vertical = 12.dp).size(22.dp), color = t.dim, strokeWidth = 2.dp)
            is ItemImage.Loaded -> Image(
                current.bitmap,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.heightIn(max = 256.dp).clip(shape).border(1.dp, t.line, shape)
                    .clickable(onClickLabel = stringResource(R.string.item_image_open)) { fullscreen = true },
            )
            else -> Row(
                Modifier.fillMaxWidth().framed(t, t.well).let {
                    if (current == ItemImage.Failed) it.clickable(onClickLabel = stringResource(R.string.item_image_retry)) { attempt++ } else it
                }.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Ic(LucideIcons.IMAGE, 14.dp, t.dim)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (current == ItemImage.Locked) R.string.item_image_locked else R.string.item_image_failed),
                    style = t.caption(11.sp),
                )
            }
        }
    }
    val loaded = image as? ItemImage.Loaded
    if (fullscreen && loaded != null) ImageFullscreen(loaded.bitmap, item.name) { fullscreen = false }
}

/** The web's full-screen image view: the picture on a dark scrim, closed by a tap anywhere or Back. */
@Composable
private fun ImageFullscreen(bitmap: ImageBitmap, name: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))
                .clickable(onClickLabel = stringResource(R.string.item_close), onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap, contentDescription = name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().widthIn(max = 1200.dp).padding(16.dp),
            )
            Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Ic(LucideIcons.CLOSE, 28.dp, Color.White.copy(alpha = 0.85f), stringResource(R.string.item_close))
            }
        }
    }
}
