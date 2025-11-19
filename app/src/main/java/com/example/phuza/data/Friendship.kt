package com.example.phuza.data

enum class FriendshipStatus {
    requested,
    follow,
    following,
    block
}

data class Friendship(
    var friendshipId: String = "",
    var user1Id: String = "",
    var user2Id: String = "",
    var status: String = "requested",
    var requestedBy: String? = null,
    var followerId: String? = null,
    var initialRequestAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)
