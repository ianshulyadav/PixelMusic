package com.unshoo.pixelmusic.data.remote.youtube

import com.unshoo.pixelmusic.data.model.youtube.PixelMusicSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object YoutubeRequestHelper {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun browse(browseId: String, settings: PixelMusicSettings): String {
        return requestWithContext(
            url = Constants.YoutubeApi.Browse.URL,
            idName = "browseId",
            id = browseId,
            settings = settings
        )
    }

    fun requestContinuation(continuationToken: String, settings: PixelMusicSettings): String {
        return requestWithContext(
            url = Constants.YoutubeApi.Browse.URL,
            idName = "continuation",
            id = continuationToken,
            settings = settings
        )
    }

    fun getPlayerInfo(videoId: String): String {
        return requestWithContext(
            url = Constants.YoutubeApi.PlayerInfo.URL,
            idName = "videoId",
            id = videoId
        )
    }

    fun search(query: String): String {
        return requestWithContext(
            url = Constants.YoutubeApi.Search.URL,
            idName = "query",
            id = query
        )
    }

    fun nextUp(videoId: String): String {
        return requestWithContext(
            url = Constants.YoutubeApi.Next.URL,
            idName = "videoId",
            id = videoId
        )
    }

    fun like(videoId: String, settings: PixelMusicSettings): String {
        return requestWithTarget(
            url = "https://www.youtube.com/youtubei/v1/like/like",
            videoId = videoId,
            settings = settings
        )
    }

    fun removeLike(videoId: String, settings: PixelMusicSettings): String {
        return requestWithTarget(
            url = "https://www.youtube.com/youtubei/v1/like/removelike",
            videoId = videoId,
            settings = settings
        )
    }

    private fun requestWithTarget(
        url: String,
        videoId: String,
        settings: PixelMusicSettings
    ): String {
        val baseBody = YoutubeAuthHelper.buildContextBody(null, null, settings)
        val body = buildJsonObject {
            baseBody.forEach { (key, value) ->
                put(key, value)
            }
            put("target", buildJsonObject {
                put("videoId", JsonPrimitive(videoId))
            })
        }

        val headers = YoutubeAuthHelper.getHeaders(settings.cookies)
        return executePost(url, body.toString(), headers)
    }

    private fun requestWithContext(
        url: String,
        idName: String,
        id: String,
        settings: PixelMusicSettings? = null
    ): String {
        val body = YoutubeAuthHelper.buildContextBody(idName, id, settings)
        val headers = if (settings != null) {
            YoutubeAuthHelper.getHeaders(settings.cookies)
        } else {
            emptyMap()
        }
        return executePost(url, body.toString(), headers)
    }

    private fun executePost(
        url: String,
        jsonBody: String,
        headers: Map<String, Any>
    ): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))

        headers.forEach { (key, value) ->
            requestBuilder.header(key, value.toString().trim())
        }

        val response = YoutubeHelper.client.newCall(requestBuilder.build()).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("YouTube API HTTP ${resp.code}: ${resp.message}")
            }
            resp.body?.string().orEmpty()
        }
    }
}
