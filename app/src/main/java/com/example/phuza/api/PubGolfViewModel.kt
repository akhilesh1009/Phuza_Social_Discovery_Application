package com.example.phuza.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class PubGolfGameViewModel : ViewModel() {

    private val api = RetrofitInstance.pubGolfApi

    private val _gameState = MutableLiveData<UiState<PubGolfGame>>(UiState.Idle)
    val gameState: LiveData<UiState<PubGolfGame>> = _gameState

    private val _invitesState = MutableLiveData<UiState<List<PubGolfInvite>>>(UiState.Idle)
    val invitesState: LiveData<UiState<List<PubGolfInvite>>> = _invitesState

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private fun setError(message: String) {
        _errorMessage.value = message
    }

    // --------------------------------------------------------
    // Create game from discover-pubs result
    // --------------------------------------------------------
    fun createGameFromDiscover(
        discoverResponse: DiscoverPubsResponse,
        title: String? = null
    ) {
        viewModelScope.launch {
            _gameState.value = UiState.Loading
            try {
                val body = CreateGameRequest(
                    title = title,
                    origin = discoverResponse.origin,
                    pubs = discoverResponse.pubs.take(9) // 9 holes max
                )

                val response = api.createGame(body)
                val envelope = response.body()

                if (response.isSuccessful && envelope?.data != null) {
                    _gameState.value = UiState.Success(envelope.data)
                } else {
                    val msg = envelope?.message ?: "Error creating game: ${response.code()}"
                    _gameState.value = UiState.Error(msg)
                    setError(msg)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error creating game"
                _gameState.value = UiState.Error(msg)
                setError(msg)
            }
        }
    }

    // --------------------------------------------------------
    // Fetch / refresh a game by id (handy for non-host users)
    // --------------------------------------------------------
    fun loadGame(gameId: String) {
        viewModelScope.launch {
            _gameState.value = UiState.Loading
            try {
                val response = api.getGame(gameId)
                val envelope = response.body()

                if (response.isSuccessful && envelope?.data != null) {
                    _gameState.value = UiState.Success(envelope.data)
                } else {
                    val msg = envelope?.message ?: "Error loading game: ${response.code()}"
                    _gameState.value = UiState.Error(msg)
                    setError(msg)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error loading game"
                _gameState.value = UiState.Error(msg)
                setError(msg)
            }
        }
    }

    // --------------------------------------------------------
    // Start game (host only): initialises scorecard
    // --------------------------------------------------------
    fun startGame(gameId: String) {
        viewModelScope.launch {
            _gameState.value = UiState.Loading
            try {
                val response = api.startGame(gameId)
                val envelope = response.body()

                if (response.isSuccessful && envelope?.data != null) {
                    _gameState.value = UiState.Success(envelope.data)
                } else {
                    val msg = envelope?.message ?: "Error starting game: ${response.code()}"
                    _gameState.value = UiState.Error(msg)
                    setError(msg)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error starting game"
                _gameState.value = UiState.Error(msg)
                setError(msg)
            }
        }
    }

    // --------------------------------------------------------
    // Update score for a player on a specific hole (host only)
    // --------------------------------------------------------
    fun updateScore(
        gameId: String,
        playerUid: String,
        holeNumber: Int,
        strokes: Int
    ) {
        viewModelScope.launch {
            try {
                val body = UpdateScoreRequest(
                    playerUid = playerUid,
                    holeNumber = holeNumber,
                    strokes = strokes
                )
                val response = api.updateScore(gameId, body)
                val envelope = response.body()

                if (response.isSuccessful && envelope?.data != null) {
                    // push updated game so scorecard UI refreshes
                    _gameState.value = UiState.Success(envelope.data)
                } else {
                    val msg = envelope?.message ?: "Error updating score: ${response.code()}"
                    // keep the current game in UI, but surface error
                    _gameState.value = UiState.Error(msg)
                    setError(msg)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error updating score"
                _gameState.value = UiState.Error(msg)
                setError(msg)
            }
        }
    }

    // --------------------------------------------------------
    // Invites
    // --------------------------------------------------------
    fun loadInvites(status: String? = "pending") {
        viewModelScope.launch {
            _invitesState.value = UiState.Loading
            try {
                val response = api.listInvites(status)
                val envelope = response.body()

                if (response.isSuccessful && envelope?.data != null) {
                    _invitesState.value = UiState.Success(envelope.data)
                } else {
                    val msg = envelope?.message ?: "Error loading invites: ${response.code()}"
                    _invitesState.value = UiState.Error(msg)
                    setError(msg)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error loading invites"
                _invitesState.value = UiState.Error(msg)
                setError(msg)
            }
        }
    }

    fun respondToInvite(inviteId: String, accept: Boolean) {
        viewModelScope.launch {
            try {
                val body = RespondInviteRequest(
                    action = if (accept) "accept" else "decline"
                )
                val response = api.respondToInvite(inviteId, body)
                val envelope = response.body()

                if (response.isSuccessful && envelope?.success != false) {
                    // After responding, refresh invites list
                    loadInvites("pending")
                } else {
                    val msg = envelope?.message ?: "Error responding to invite: ${response.code()}"
                    _invitesState.value = UiState.Error(msg)
                    setError(msg)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error responding to invite"
                _invitesState.value = UiState.Error(msg)
                setError(msg)
            }
        }
    }

    /**
     * Host sends invites to a list of user ids.
     *
     * onResult(success, errorMessageOrNull)
     */
    fun sendInvites(
        gameId: String,
        toUserIds: List<String>,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (toUserIds.isEmpty()) {
            onResult(false, "Select at least one friend")
            return
        }

        viewModelScope.launch {
            try {
                val body = SendInvitesRequest(toUserIds = toUserIds)
                val response = api.sendInvites(gameId, body)
                val envelope = response.body()

                if (response.isSuccessful && envelope?.success != false) {
                    onResult(true, null)
                } else {
                    val msg = envelope?.message ?: "Error sending invites: ${response.code()}"
                    setError(msg)
                    onResult(false, msg)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error sending invites"
                setError(msg)
                onResult(false, msg)
            }
        }
    }

    // --------------------------------------------------------
    // END GAME
    // --------------------------------------------------------

    fun finishGame(gameId: String) {
        viewModelScope.launch {
            _gameState.value = UiState.Loading
            try {
                val response = api.finishGame(gameId)
                val envelope = response.body()
                if (response.isSuccessful && envelope?.data != null) {
                    _gameState.value = UiState.Success(envelope.data)
                } else {
                    _gameState.value =
                        UiState.Error(envelope?.message ?: "Error: ${response.code()}")
                }
            } catch (e: Exception) {
                _gameState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
