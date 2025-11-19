package com.example.phuza.api

import com.example.phuza.data.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// (GeeksforGeeks 2023; Firebase 2019c; Mapbox 2025)
interface ApiService {
    @GET("/")
    suspend fun health(): Response<Unit>

    // Discover pubs based on location (Mapbox 2025)
    @POST("api/discover-pubs")
    suspend fun discoverPubs(
        @Body body: DiscoverPubsRequest
    ): Response<ApiEnvelope<DiscoverPubsResponse>>

    // Retrieve user list for friends module (Firebase 2019d)
    @GET("api/friends")
    suspend fun listUsers(
        @Query("q") q: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("skip") skip: Int = 0
    ): Response<ApiEnvelope<List<UserDto>>>

    @GET("api/friends/me")
    suspend fun listMyFriends(): Response<ApiEnvelope<List<String>>>

    @GET("api/friends/me/details")
    suspend fun listMyFriendsDetailed(): Response<ApiEnvelope<List<UserDto>>>

    // Add a friend (current user -> friendId)
    @POST("api/friends/{friendId}")
    suspend fun addFriend(
        @Path("friendId") friendId: String
    ): Response<ApiEnvelope<Map<String, Any>>>

    // Remove a friend
    @DELETE("api/friends/{friendId}")
    suspend fun removeFriend(
        @Path("friendId") friendId: String
    ): Response<ApiEnvelope<Map<String, Any>>>

    // Send follow request notification (Firebase 2019c)
    @POST("api/notify/follow-request")
    suspend fun notifyFollowRequest(@Body body: Map<String, String>): Response<ApiEnvelope<Unit>>

    // Notify when follow request accepted (Firebase 2019c)
    @POST("api/notify/follow-accept")
    suspend fun notifyFollowAccept(@Body body: Map<String, String>): Response<ApiEnvelope<Unit>>
}

/*
 * REFERENCES
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”.
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 * Mapbox. 2025. “Mapbox Docs”.
 * https://docs.mapbox.com/#search
 * [accessed 16 September 2025].
 */
