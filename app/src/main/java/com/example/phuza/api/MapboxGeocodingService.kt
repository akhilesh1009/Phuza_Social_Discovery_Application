package com.example.phuza.api

import com.google.gson.annotations.SerializedName // (Ananth.k 2023)
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory // (GeeksforGeeks 2023)
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class MbxGeocodingResponse(
    val features: List<MbxFeature> = emptyList()
)

data class MbxFeature(
    val id: String,
    @SerializedName("place_name") val placeName: String, // (Ananth.k 2023)
    val text: String,
    val center: List<Double>?,
    @SerializedName("place_type") val placeType: List<String>?, // (Ananth.k 2023)
    val geometry: MbxGeometry?
) {
    fun lon(): Double? = center?.getOrNull(0) ?: geometry?.coordinates?.getOrNull(0)
    fun lat(): Double? = center?.getOrNull(1) ?: geometry?.coordinates?.getOrNull(1)
}

data class MbxGeometry(
    val type: String,
    val coordinates: List<Double>
)

interface MapboxGeocodingApi {
    @GET("geocoding/v5/mapbox.places/{query}.json") // (Mapbox 2025)
    suspend fun forward(
        @Path("query") query: String,
        @Query("access_token") token: String,
        @Query("limit") limit: Int = 6,
        @Query("proximity") proximity: String? = null,   // "lon,lat" (bias results near user) (Mapbox 2025)
        @Query("types") types: String? = null,           // e.g., "poi,place" (Mapbox 2025)
        @Query("categories") categories: String? = null, // e.g., "bar,pub,brewery,nightclub" (Mapbox 2025)
        @Query("country") country: String? = null,       // e.g., "ZA" (Mapbox 2025)
        @Query("language") language: String? = null      // e.g., "en" (Mapbox 2025)
    ): Response<MbxGeocodingResponse>
}

object MapboxGeocodingService {
    private const val BASE_URL = "https://api.mapbox.com/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    private val ok = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(ok)
        .addConverterFactory(GsonConverterFactory.create()) // (GeeksforGeeks 2023)
        .build()

    val api: MapboxGeocodingApi = retrofit.create(MapboxGeocodingApi::class.java) // (GeeksforGeeks 2023)
}

/*
 * REFERENCES
 *
 * Ananth.k. 2023. “Kotlin — SerializedName Annotation”.
 * https://medium.com/@ananthkvn2016/kotlin-serializedname-annotation-2ad375f83371
 * [accessed 19 September 2025].
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”.
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 * Mapbox. 2025. “Mapbox Docs”.
 * https://docs.mapbox.com/#search
 * [accessed 16 September 2025].
 */
