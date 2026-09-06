package com.unshoo.pixelmusic.di

import com.unshoo.pixelmusic.data.listenbrainz.ListenBrainzApiService
import com.unshoo.pixelmusic.data.listenbrainz.ListenBrainzEndpoint
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @ListenBrainzRetrofit
    fun provideListenBrainzRetrofit(
        okHttpClient: OkHttpClient,
        endpoint: ListenBrainzEndpoint
    ): Retrofit {
        val client = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val rewritten = endpoint.rewrite(request.url)
                if (rewritten == request.url) {
                    chain.proceed(request)
                } else {
                    chain.proceed(request.newBuilder().url(rewritten).build())
                }
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(ListenBrainzEndpoint.DEFAULT_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideListenBrainzApiService(@ListenBrainzRetrofit retrofit: Retrofit): ListenBrainzApiService {
        return retrofit.create(ListenBrainzApiService::class.java)
    }
}
