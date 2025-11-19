package com.example.phuza.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.phuza.offline.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE synced = 0 AND outbound = 1 ORDER BY timeSent ASC")
    suspend fun getPendingOutbound(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: List<MessageEntity>)

    @Query("SELECT MAX(timeSent) FROM messages WHERE inbound = 1 AND recipientId = :uid")
    suspend fun maxInboundTs(uid: String): Long?

    @Query(
        """
        SELECT * FROM messages
        WHERE chatId = :chatId
        ORDER BY timeSent ASC
        """
    )
    fun observeChat(chatId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE senderId = :uid OR recipientId = :uid
        ORDER BY timeSent DESC
        """
    )
    suspend fun getAllForUser(uid: String): List<MessageEntity>

    @Query("""
        SELECT * FROM messages
        WHERE senderId = :uid OR recipientId = :uid
        ORDER BY timeSent DESC
    """)
    suspend fun latestForUser(uid: String): List<MessageEntity>

    @Query("""
        SELECT * FROM messages
        WHERE senderId = :uid OR recipientId = :uid
        ORDER BY timeSent DESC
    """)
    suspend fun latestForUserFlat(uid: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun countForChat(chatId: String): Int

}
