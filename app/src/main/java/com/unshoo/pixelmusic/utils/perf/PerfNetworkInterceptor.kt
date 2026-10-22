package com.unshoo.pixelmusic.utils.perf

import android.os.SystemClock
import com.unshoo.pixelmusic.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class PerfNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!BuildConfig.DEBUG) return chain.proceed(chain.request())

        val request = chain.request()
        val url = request.url.toString()
        val method = request.method
        val reqId = PerfTracker.onNetworkRequestStart(url, method)
        val startTime = SystemClock.elapsedRealtime()

        return try {
            val response = chain.proceed(request)
            val duration = SystemClock.elapsedRealtime() - startTime
            val bodySize = response.body?.contentLength() ?: 0L
            val isCache = response.networkResponse == null && response.cacheResponse != null

            PerfTracker.onNetworkRequestEnd(
                reqId = reqId,
                url = url,
                method = method,
                durationMs = duration,
                statusCode = response.code,
                responseBytes = bodySize,
                isSuccess = response.isSuccessful,
                cacheHit = isCache
            )
            response
        } catch (e: IOException) {
            val duration = SystemClock.elapsedRealtime() - startTime
            PerfTracker.onNetworkRequestEnd(
                reqId = reqId,
                url = url,
                method = method,
                durationMs = duration,
                statusCode = -1,
                responseBytes = 0L,
                isSuccess = false,
                errorMsg = e.message
            )
            throw e
        }
    }
}
