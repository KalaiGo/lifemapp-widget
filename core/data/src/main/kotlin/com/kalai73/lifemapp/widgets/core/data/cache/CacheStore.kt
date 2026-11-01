package com.kalai73.lifemapp.widgets.core.data.cache

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.kalai73.lifemapp.widgets.core.data.LifeMappJson
import com.kalai73.lifemapp.widgets.core.model.CachedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object CachedStateSerializer : Serializer<CachedState> {
    override val defaultValue = CachedState()

    override suspend fun readFrom(input: InputStream): CachedState = try {
        LifeMappJson.decodeFromString(CachedState.serializer(), input.readBytes().decodeToString())
    } catch (e: Exception) {
        throw CorruptionException("Unreadable widget cache", e)
    }

    override suspend fun writeTo(t: CachedState, output: OutputStream) {
        output.write(LifeMappJson.encodeToString(CachedState.serializer(), t).encodeToByteArray())
    }
}

/**
 * The widgets' local snapshot cache. Widgets render only from here; network sync writes here.
 * End-to-end encrypted fields stay encrypted at rest and are decrypted at render time.
 */
class CacheStore(private val store: DataStore<CachedState>) {
    constructor(context: Context) : this(
        DataStoreFactory.create(
            serializer = CachedStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { CachedState() },
            produceFile = { File(context.noBackupFilesDir, "widget_cache.json") },
        )
    )

    val state: Flow<CachedState> = store.data

    suspend fun current(): CachedState = store.data.first()

    suspend fun update(transform: (CachedState) -> CachedState): CachedState = store.updateData(transform)

    /** Drops the account data; the device's theme and color scheme choices survive sign-out. */
    suspend fun clear() {
        store.updateData { CachedState(themeMode = it.themeMode, colorSchemeOverride = it.colorSchemeOverride) }
    }
}
