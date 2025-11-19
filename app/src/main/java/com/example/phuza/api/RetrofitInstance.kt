package com.example.phuza.api

import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory // (GeeksforGeeks 2023)
import java.util.concurrent.TimeUnit

// (GeeksforGeeks 2023)

object RetrofitInstance {
    private const val BASE_URL = "https://location-api-cng2.onrender.com/"

    // Interceptor that injects the Firebase user UID into every request
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid

        if (!uid.isNullOrEmpty()) {
            // This is what the backend will read
            builder.header("x-user-id", uid)
        }

        chain.proceed(builder.build())
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // FIRST add our auth interceptor
            .addInterceptor(authInterceptor)
            // THEN logging (so you can actually see the header in logs)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy { retrofit.create(ApiService::class.java) }
    val messageApi: MessagesApi by lazy { retrofit.create(MessagesApi::class.java) }
    val pubGolfApi: PubGolfApi by lazy { retrofit.create(PubGolfApi::class.java) }
}



/*
 * REFERENCES
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”.
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 */
