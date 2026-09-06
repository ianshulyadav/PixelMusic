package com.unshoo.pixelmusic.ui.glancewidget

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.datastore.preferences.protobuf.InvalidProtocolBufferException
import androidx.glance.state.GlanceStateDefinition
import java.io.InputStream
import java.io.OutputStream
import com.unshoo.pixelmusic.data.model.PlayerInfo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.IOException
import java.io.File

object PlayerInfoStateDefinition : GlanceStateDefinition<PlayerInfo> {
    private const val DATASTORE_FILE_NAME = "pixelPlayPlayerInfo_v1_json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val Context.playerInfoDataStore: DataStore<PlayerInfo> by dataStore(
        fileName = DATASTORE_FILE_NAME,
        serializer = PlayerInfoJsonSerializer(json)
    )

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<PlayerInfo> {
        return context.playerInfoDataStore
    }

    override fun getLocation(context: Context, fileKey: String): File {
        return File(context.filesDir, "datastore/$DATASTORE_FILE_NAME")
    }
}

class PlayerInfoJsonSerializer(private val json: Json) : Serializer<PlayerInfo> {
    override val defaultValue: PlayerInfo = PlayerInfo()

    override suspend fun readFrom(input: InputStream): PlayerInfo {
        try {
            val string = input.bufferedReader().use { it.readText() }
            if (string.isBlank()) return defaultValue
            return json.decodeFromString<PlayerInfo>(string)
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read json.", exception)
        } catch (e: IOException) {
            throw CorruptionException("Cannot read json due to IO issue.", e)
        }
    }

    override suspend fun writeTo(t: PlayerInfo, output: OutputStream) {
        output.bufferedWriter().use {
            it.write(json.encodeToString(t))
        }
    }
}