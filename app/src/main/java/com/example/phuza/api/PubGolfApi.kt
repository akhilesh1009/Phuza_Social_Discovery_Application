package com.example.phuza.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ---- Requests ----

data class CreateGameRequest(
    val title: String? = null,
    val origin: StartingPoint,
    val pubs: List<DiscoveredPub>
)

data class SendInvitesRequest(
    val toUserIds: List<String>
)

data class RespondInviteRequest(
    val action: String // "accept" or "decline"
)

data class UpdateScoreRequest(
    val playerUid: String,
    val holeNumber: Int, // 1..9
    val strokes: Int
)

// ---- Game model ----

data class PubGolfDrink(
    val id: String,
    val name: String,
    val par: Int
)

data class PubGolfHole(
    val holeNumber: Int,
    val name: String,
    val address: String,
    val coordinates: Coordinates,
    val par: Int,
    val drinks: List<PubGolfDrink> = emptyList(),
    val waterHazard: Boolean = false,
    val bunkerHazard: Boolean = false
)

data class PubGolfPlayer(
    val uid: String,
    val role: String, // "host" | "player"
    val name: String? = null,
    val joinedAt: Any? = null,
    val strokes: List<Int?>? = null,  // one per hole (may be null before input)
    val totalStrokes: Int? = null,
    val scoreToPar: Int? = null
)

data class PubGolfGame(
    val id: String,
    val title: String,
    val hostUid: String,
    val hostName: String? = null,
    val origin: StartingPoint,
    val holes: List<PubGolfHole>,
    val status: String, // "pending" | "active" | "finished"
    val players: List<PubGolfPlayer> = emptyList(),
    val createdAt: Any? = null // Firebase timestamp
)

// ---- Invites model ----

data class PubGolfInvite(
    val id: String,
    val gameId: String,
    val fromUid: String,
    val toUid: String,
    val status: String, // "pending" | "accepted" | "declined"
    val createdAt: Any? = null,
    val respondedAt: Any? = null
)

// ---- API interface ----
interface PubGolfApi {

    // Create a new game
    @POST("api/pubgolf/games")
    suspend fun createGame(
        @Body body: CreateGameRequest
    ): Response<ApiEnvelope<PubGolfGame>>

    // Fetch a single game by id
    @GET("api/pubgolf/games/{gameId}")
    suspend fun getGame(
        @Path("gameId") gameId: String
    ): Response<ApiEnvelope<PubGolfGame>>

    // Start game (host only)
    @POST("api/pubgolf/games/{gameId}/start")
    suspend fun startGame(
        @Path("gameId") gameId: String
    ): Response<ApiEnvelope<PubGolfGame>>

    // Update score for a player/hole (host only)
    @POST("api/pubgolf/games/{gameId}/score")
    suspend fun updateScore(
        @Path("gameId") gameId: String,
        @Body body: UpdateScoreRequest
    ): Response<ApiEnvelope<PubGolfGame>>

    // Send invites for a game
    @POST("api/pubgolf/games/{gameId}/invites")
    suspend fun sendInvites(
        @Path("gameId") gameId: String,
        @Body body: SendInvitesRequest
    ): Response<ApiEnvelope<Map<String, Any>>>

    // List invites for current user
    @GET("api/pubgolf/invites")
    suspend fun listInvites(
        @Query("status") status: String? = null
    ): Response<ApiEnvelope<List<PubGolfInvite>>>

    // Respond to an invite
    @POST("api/pubgolf/invites/{inviteId}/respond")
    suspend fun respondToInvite(
        @Path("inviteId") inviteId: String,
        @Body body: RespondInviteRequest
    ): Response<ApiEnvelope<Map<String, Any>>>

    // Finish game (host only)
    @POST("api/pubgolf/games/{gameId}/finish")
    suspend fun finishGame(
        @Path("gameId") gameId: String
    ): Response<ApiEnvelope<PubGolfGame>>
}

