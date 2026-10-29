package com.haseebflix.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.haseebflix.app.BuildConfig
import com.haseebflix.app.data.local.AppDatabase
import com.haseebflix.app.data.remote.TmdbApi
import com.haseebflix.app.playback.NoPlaybackSource
import com.haseebflix.app.playback.PlaybackSource
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }

    @Provides @Singleton
    fun okHttp(@ApplicationContext ctx: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(ctx.cacheDir, "http"), 10L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val orig = chain.request()
            val token = BuildConfig.TMDB_TOKEN.trim()
            val req = if (token.length > 50 || token.startsWith("eyJ")) {
                orig.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("accept", "application/json")
                    .build()
            } else {
                val url = orig.url.newBuilder().addQueryParameter("api_key", token).build()
                orig.newBuilder()
                    .url(url)
                    .header("accept", "application/json")
                    .build()
            }
            chain.proceed(req)
        }.build()

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/").client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()

    @Provides @Singleton fun api(retrofit: Retrofit): TmdbApi = retrofit.create(TmdbApi::class.java)

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "haseebflix.db").fallbackToDestructiveMigration().build()

    @Provides @Singleton
    fun dataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { ctx.preferencesDataStoreFile("settings") })

    /** Swap this for your own licensed implementation (see PlaybackSource.kt). */
    @Provides @Singleton fun playbackSource(): PlaybackSource = NoPlaybackSource()
}
