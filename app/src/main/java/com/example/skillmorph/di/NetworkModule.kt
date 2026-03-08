package com.example.skillmorph.di

import com.example.skillmorph.data.remote.SkillMorphApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ⚠️ REPLACE THIS WITH YOUR CURRENT PINGGY/NGROK URL
    // MUST END WITH A SLASH /
    private const val BASE_URL = "https://gvcddhg2y7sis2oajtn7pj3cv40tphjn.lambda-url.ap-south-1.on.aws/"

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideSkillMorphApi(retrofit: Retrofit): SkillMorphApi {
        return retrofit.create(SkillMorphApi::class.java)
    }
}