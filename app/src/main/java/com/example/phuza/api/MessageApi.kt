package com.example.phuza.api

import com.example.phuza.dto.ChatPageDto
import com.example.phuza.dto.MessageDto
import retrofit2.Response
import retrofit2.http.*

data class SendMessagePayload(
    val fromUid: String,
    val toUid: String,
    val body: String,
    val clientId: String? = null
)

data class Meta(val order: String?, val limit: Int?)

interface MessagesApi {

    @POST("/api/messages")
    suspend fun send(@Body payload: SendMessagePayload): Response<MessageDto>

    @GET("/api/messages")
    suspend fun listWithPeer(
        @Query("peerId") peerId: String,
        @Query("uid") uid: String,
        @Query("limit") limit: Int? = null
    ): Response<List<MessageDto>>

    @GET("/api/messages/since")
    suspend fun listSince(
        @Query("since") sinceEpochMs: Long,
        @Query("uid") uid: String?
    ): Response<List<MessageDto>>

    @GET("/api/messages/{chatId}")
    suspend fun listByChat(
        @Path("chatId") chatId: String,
        @Query("uid") uid: String,
        @Query("limit") limit: Int? = 100,
        @Query("after") afterEpochMs: Long? = null
    ): Response<ChatPageDto>
}
