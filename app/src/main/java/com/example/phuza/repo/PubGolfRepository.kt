package com.example.phuza.repo

import com.example.phuza.api.CreateGameRequest
import com.example.phuza.api.PubGolfApi
import com.example.phuza.api.RespondInviteRequest
import com.example.phuza.api.RetrofitInstance
import com.example.phuza.api.SendInvitesRequest
import com.example.phuza.api.UpdateScoreRequest

class PubGolfRepository(
    private val api: PubGolfApi = RetrofitInstance.pubGolfApi
) {

    suspend fun createGame(body: CreateGameRequest) =
        api.createGame(body)

    suspend fun getGame(gameId: String) =
        api.getGame(gameId)

    suspend fun startGame(gameId: String) =
        api.startGame(gameId)

    suspend fun updateScore(gameId: String, body: UpdateScoreRequest) =
        api.updateScore(gameId, body)

    suspend fun sendInvites(gameId: String, body: SendInvitesRequest) =
        api.sendInvites(gameId, body)

    suspend fun listInvites(status: String? = null) =
        api.listInvites(status)

    suspend fun respondToInvite(inviteId: String, body: RespondInviteRequest) =
        api.respondToInvite(inviteId, body)
}
