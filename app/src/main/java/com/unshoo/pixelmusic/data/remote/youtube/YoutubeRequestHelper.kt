package com.unshoo.pixelmusic.data.remote.youtube

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import com.unshoo.pixelmusic.data.model.youtube.PixelMusicSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray

object YoutubeRequestHelper {
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

        val (_, _, result) = url.httpPost().jsonBody(body.toString())
            .header(headers)
            .responseJson()

        return when (result) {
            is Result.Success -> {
                result.value.content
            }

            is Result.Failure -> {
                throw result.error.exception
            }
        }
    }

    private fun requestWithContext(
        url: String,
        idName: String,
        id: String,
        settings: PixelMusicSettings? = null
    ): String {
        val body =
            YoutubeAuthHelper.buildContextBody(idName, id, settings)

        val headers = if (settings != null) {
            YoutubeAuthHelper.getHeaders(settings.cookies)
        } else {
            mapOf()
        }

        val (_, _, result) = url.httpPost().jsonBody(body.toString())
            .header(
                headers
            )
            .responseJson()

        return when (result) {
            is Result.Success -> {
                result.value.content
            }

            is Result.Failure -> {
                throw result.error.exception
            }
        }
    }
}
