package com.example.phuza.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val chatId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val timeSent: Long,
    val outbound: Boolean,
    val inbound: Boolean,
    val synced: Boolean = false,

    // User info for offline
    val peerUid: String? = null,
    val peerName: String? = null,
    val peerUsername: String? = null,
    val peerAvatarBase64: String? = null
)
