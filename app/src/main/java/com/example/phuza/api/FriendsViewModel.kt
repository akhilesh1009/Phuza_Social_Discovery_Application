package com.example.phuza.api

import androidx.lifecycle.*
import com.example.phuza.data.FriendshipStatus
import com.example.phuza.data.UserDto
import com.example.phuza.repo.FriendsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// (Android Developers 2025; Firebase 2019a; Firebase 2019d)
class FriendsViewModel(
    private val repo: FriendsRepository
) : ViewModel() {

    private val _usersState = MutableLiveData<UiState<List<UserDto>>>(UiState.Idle)
    val usersState: LiveData<UiState<List<UserDto>>> = _usersState

    private val _statusMap = MutableLiveData<Map<String, FriendshipStatus>>(emptyMap())
    val statusMap: LiveData<Map<String, FriendshipStatus>> = _statusMap
    private val _incomingSet = MutableLiveData<Set<String>>(emptySet())
    val incomingSet: LiveData<Set<String>> = _incomingSet
    private val _actionState = MutableLiveData<UiState<Unit>>(UiState.Idle)
    private var statusJob: Job? = null
    private var cachedUsers: List<UserDto>? = null
    fun hasCache(): Boolean = !cachedUsers.isNullOrEmpty()

    init {
        // Observing Firestore changes using coroutines (Firebase 2019a)
        statusJob = viewModelScope.launch {
            repo.observeMyFriendships().collectLatest { result ->
                _statusMap.postValue(result.statusByOtherUid)
                _incomingSet.postValue(result.incomingRequests)
            }
        }
    }

    fun loadUsers(query: String? = null) {
        val q = query?.trim().orEmpty()

        cachedUsers?.let { all ->
            if (q.isEmpty()) {
                _usersState.value = UiState.Success(all)
            } else {
                val qLower = q.lowercase()
                val filtered = all.filter { u ->
                    (u.username?.lowercase()?.contains(qLower) == true) ||
                            (u.firstName?.lowercase()?.contains(qLower) == true) ||
                            (u.name?.lowercase()?.contains(qLower) == true) ||
                            (u.email?.lowercase()?.contains(qLower) == true)
                }
                _usersState.value = UiState.Success(filtered)
            }
            return
        }

        if (q.isEmpty()) _usersState.value = UiState.Loading

        viewModelScope.launch {
            runCatching { repo.listUsers(null) }
                .onSuccess { env ->
                    if (env.success && env.data != null) {
                        val me = FirebaseAuth.getInstance().currentUser
                        val myUid = me?.uid
                        val myEmail = me?.email?.lowercase()
                        val myName = me?.displayName

                        val all = env.data.filter { u ->
                            val isMeByUid = myUid != null && (u.uid == myUid || u.id == myUid)
                            val isMeByEmail = myEmail != null && u.email?.lowercase() == myEmail
                            val isMeByUsername = myName != null && u.username == myName
                            !(isMeByUid || isMeByEmail || isMeByUsername)
                        }
                        cachedUsers = all
                        loadUsers(query)
                    } else {
                        _usersState.value = UiState.Error(env.message ?: "Failed to load users")
                    }
                }
                .onFailure {
                    _usersState.value = UiState.Error(it.message ?: "Error loading users")
                }
        }
    }

    fun sendRequest(otherUid: String) = runAction { repo.sendFriendRequest(otherUid) } // (Firebase 2019c)
    fun acceptRequest(otherUid: String) = runAction { repo.acceptFriendRequest(otherUid) } // (Firebase 2019c)
    fun rejectRequest(otherUid: String) = runAction { repo.rejectFriendRequest(otherUid) } // (Firebase 2019c)
    fun unfollow(otherUid: String) = runAction { repo.unfollow(otherUid) } // (Firebase 2019c)

    fun cancelRequest(otherUid: String) = viewModelScope.launch {
        try {
            repo.cancelFriendRequest(otherUid)
            val cur = statusMap.value?.toMutableMap() ?: mutableMapOf()
            cur.remove(otherUid)
            _statusMap.value = cur
        } catch (e: Exception) {
            _usersState.value = UiState.Error(e.message ?: "Failed to cancel request")
        }
    }

    private inline fun runAction(crossinline block: suspend () -> Unit) {
        _actionState.value = UiState.Loading
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _actionState.value = UiState.Success(Unit) }
                .onFailure { _actionState.value = UiState.Error(it.message ?: "Action failed") }
        }
    }

    override fun onCleared() {
        statusJob?.cancel()
        super.onCleared()
    }
}

// (Android Developers 2025; Firebase 2019b)
class FriendsVMFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val api = RetrofitInstance.api
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val repo = FriendsRepository(api, auth, db)
        @Suppress("UNCHECKED_CAST")
        return FriendsViewModel(repo) as T
    }
}

/*
 * REFERENCES
 *
 * Android Developers. 2025. “Guide to ViewModel and LiveData”.
 * https://developer.android.com/topic/libraries/architecture/viewmodel
 * [accessed 25 September 2025].
 *
 * Firebase. 2019a. “Cloud Firestore | Firebase”.
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 */
