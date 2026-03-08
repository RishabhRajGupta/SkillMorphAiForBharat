package com.example.skillmorph.di // or your package

import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class AuthInterceptor @Inject constructor() : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 1. Get the current Firebase User ID
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid ?: "anonymous_user" // Fallback if not logged in

        // 2. Add it as a Header to the request
        val newRequest = originalRequest.newBuilder()
            .addHeader("x-user-id", uid) // We send it as "x-user-id"
            .build()

        return chain.proceed(newRequest)
    }
}