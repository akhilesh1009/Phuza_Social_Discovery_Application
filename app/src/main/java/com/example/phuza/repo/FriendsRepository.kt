package com.example.phuza.repo

import com.example.phuza.api.ApiEnvelope
import com.example.phuza.api.ApiService
import com.example.phuza.data.Friendship
import com.example.phuza.data.FriendshipStatus
import com.example.phuza.data.UserDto
import com.google.firebase.auth.FirebaseAuth // (Firebase 2019b)
import com.google.firebase.firestore.FirebaseFirestore // (Firebase 2019a)
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

class FriendsRepository(
    private val api: ApiService,
    private val auth: FirebaseAuth,            // (Firebase 2019b)
    private val db: FirebaseFirestore          // (Firebase 2019a)
) {
    private val meUid: String?
        get() = auth.currentUser?.uid // (Firebase 2019b)

    suspend fun listUsers(q: String?): ApiEnvelope<List<UserDto>> {
        val resp = api.listUsers(q = q)
        return resp.body() ?: ApiEnvelope(success = false, message = "No body")
    }

    suspend fun getFriendsFromApi(): List<String> {
        val resp = api.listMyFriends()
        val env = resp.body()
        return if (resp.isSuccessful && env?.success == true && env.data != null) {
            env.data
        } else {
            emptyList()
        }
    }


    suspend fun sendFriendRequest(otherUid: String) {
        val myUid = meUid ?: error("Not logged in")
        val (a, b) = listOf(myUid, otherUid).sorted()
        val pairId = "${a}_${b}"
        val docRef = db.collection("friendships").document(pairId) // (Firebase 2019a)

        db.runTransaction { tx -> // (Firebase 2019a)
            val snap = tx.get(docRef)
            val now = System.currentTimeMillis()

            if (snap.exists()) {
                val status = snap.getString("status")
                val requestedBy = snap.getString("requestedBy")

                when (status) {
                    "requested" -> {
                        if (requestedBy != myUid) {
                            throw IllegalStateException("This user already sent you a request. Accept or reject it.")
                        }
                        tx.update(docRef, mapOf("updatedAt" to now))
                    }
                    "accepted" -> {
                    }
                    else -> Unit
                }
            } else {
                // Create new pending request from me
                val data = mapOf(
                    "friendshipId" to pairId,
                    "user1Id" to a,
                    "user2Id" to b,
                    "status" to "requested",
                    "requestedBy" to myUid,
                    "followerId" to null,
                    "initialRequestAt" to now,
                    "updatedAt" to now
                )
                tx.set(docRef, data)
            }
            null
        }.await()
        notifyFollowRequest(senderUid = myUid, receiverUid = otherUid)
    }

    suspend fun acceptFriendRequest(otherUid: String) {
        val myUid = meUid ?: error("Not logged in")
        val (a, b) = listOf(myUid, otherUid).sorted()
        val pairId = "${a}_${b}"
        val now = System.currentTimeMillis()

        db.collection("friendships").document(pairId) // (Firebase 2019a)
            .update(
                mapOf(
                    "status" to "accepted",
                    "followerId" to myUid,
                    "updatedAt" to now
                )
            )
            .await()
        notifyFollowAccepted(accepterUid = myUid, requesterUid = otherUid)
    }

    suspend fun rejectFriendRequest(otherUid: String) {
        val myUid = meUid ?: error("Not logged in")
        val (a, b) = listOf(myUid, otherUid).sorted()
        val pairId = "${a}_${b}"
        db.collection("friendships").document(pairId).delete().await() // (Firebase 2019a)
    }

    suspend fun unfollow(otherUid: String) {
        val myUid = meUid ?: error("Not logged in")
        val (a, b) = listOf(myUid, otherUid).sorted()
        val pairId = "${a}_${b}"
        db.collection("friendships").document(pairId).delete().await() // (Firebase 2019a)
    }

    data class ObserveResult(
        val statusByOtherUid: Map<String, FriendshipStatus>,
        val incomingRequests: Set<String>
    )

    fun observeMyFriendships(): Flow<ObserveResult> {
        val myId = meUid ?: return flowOf(ObserveResult(emptyMap(), emptySet()))

        val col = db.collection("friendships") // (Firebase 2019a)
        val flow1 = col.whereEqualTo("user1Id", myId).snapshotsAsFlow()
        val flow2 = col.whereEqualTo("user2Id", myId).snapshotsAsFlow()

        return combine(flow1, flow2) { s1, s2 ->
            val map = mutableMapOf<String, FriendshipStatus>()
            val incoming = mutableSetOf<String>()

            val docs = (s1?.documents.orEmpty()) + (s2?.documents.orEmpty())
            for (doc in docs) {
                val f = doc.toObject(Friendship::class.java) ?: continue
                val otherId = if (f.user1Id == myId) f.user2Id else f.user1Id

                when (f.status) {
                    "requested" -> {
                        if (f.requestedBy == myId) {
                            map[otherId] = FriendshipStatus.requested // I sent it
                        } else {
                            incoming += otherId                       // I can accept/decline
                            map[otherId] = FriendshipStatus.requested
                        }
                    }
                    "accepted" -> {
                        map[otherId] = FriendshipStatus.follow
                    }
                }
            }
            ObserveResult(map.toMap(), incoming.toSet())
        }
    }

    private suspend fun notifyFollowRequest(senderUid: String, receiverUid: String) {
        api.notifyFollowRequest(mapOf("toUid" to receiverUid, "fromUid" to senderUid))
    }

    private suspend fun notifyFollowAccepted(accepterUid: String, requesterUid: String) {
        api.notifyFollowAccept(mapOf("toUid" to requesterUid, "fromUid" to accepterUid))
    }

    suspend fun cancelFriendRequest(otherUid: String) {
        val myUid = meUid ?: error("Not logged in")
        val (a, b) = listOf(myUid, otherUid).sorted()
        val pairId = "${a}_${b}"
        val doc = db.collection("friendships").document(pairId) // (Firebase 2019a)

        val snap = doc.get().await()
        if (!snap.exists()) return

        val status = snap.getString("status")
        val requestedBy = snap.getString("requestedBy")

        if (status == "requested" && requestedBy == myUid) {
            doc.delete().await() // or set status back to null if you keep the doc
        }
    }
}

private fun Query.snapshotsAsFlow(): Flow<QuerySnapshot?> = callbackFlow { // (Firebase 2019a)
    val reg = addSnapshotListener { value, error ->
        if (error != null) {
            trySend(null)
            return@addSnapshotListener
        }
        trySend(value)
    }
    awaitClose { reg.remove() }
}

/*
 * REFERENCES
 *
 * Firebase. 2019a. “Cloud Firestore | Firebase”.
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 */
