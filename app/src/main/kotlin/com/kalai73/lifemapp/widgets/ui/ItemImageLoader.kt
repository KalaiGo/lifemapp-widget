package com.kalai73.lifemapp.widgets.ui

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import com.kalai73.lifemapp.widgets.core.data.repository.ItemImageRef
import com.kalai73.lifemapp.widgets.core.data.repository.ItemRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Image items' pictures for the Today screen: where a picture is comes fresh from its row (it may have been replaced
 * on the web), the picture itself from a memory cache when already shown.
 */
internal class ItemImageLoader(private val items: ItemRepository) {
    /** Decoded pictures by URI (uploads get unique names, so a URI's picture doesn't change), sized in bytes */
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Never throws: failures are [ItemImage.Failed]. */
    suspend fun load(itemId: String): ItemImage = try {
        when (val ref = items.imageUri(itemId)) {
            ItemImageRef.Locked -> ItemImage.Locked
            ItemImageRef.Missing -> ItemImage.Failed
            is ItemImageRef.Found -> {
                val bitmap = cache.get(ref.uri)
                    ?: items.imageBytes(ref.uri)
                        ?.let { bytes -> withContext(Dispatchers.Default) { decodeItemImage(bytes) } }
                        ?.also { cache.put(ref.uri, it) }
                bitmap?.let { ItemImage.Loaded(it) } ?: ItemImage.Failed
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ItemImage.Failed
    }
}
