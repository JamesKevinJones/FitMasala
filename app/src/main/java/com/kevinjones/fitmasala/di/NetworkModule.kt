package com.kevinjones.fitmasala.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kevinjones.fitmasala.BuildConfig
import com.kevinjones.fitmasala.data.remote.AnthropicAuthInterceptor
import com.kevinjones.fitmasala.data.remote.GeminiAuthInterceptor
import com.kevinjones.fitmasala.data.remote.api.AnthropicApi
import com.kevinjones.fitmasala.data.remote.api.GeminiApi
import com.kevinjones.fitmasala.data.remote.apiJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = apiJson()

    @Provides
    @Singleton
    fun provideOkHttp(auth: AnthropicAuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(auth)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            // BASIC, never BODY. A BODY log writes the request
                            // headers to logcat, and the API key is a header.
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            // Generous: a high-effort request with adaptive thinking and a photo
            // attached genuinely takes a while, and a timeout mid-thought costs a
            // full call's tokens for nothing.
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(AnthropicApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAnthropicApi(retrofit: Retrofit): AnthropicApi =
        retrofit.create(AnthropicApi::class.java)

    /**
     * Gemini gets its own OkHttp client so its interceptor - and so its key -
     * only ever sees Gemini requests. The Anthropic client above never carries
     * the Gemini key, nor this one the Anthropic key.
     */
    @Provides
    @Singleton
    fun provideGeminiApi(auth: GeminiAuthInterceptor, json: Json): GeminiApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC only: the key is a header, and a URL never carries it.
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        return Retrofit.Builder()
            .baseUrl(GeminiApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApi::class.java)
    }
}
