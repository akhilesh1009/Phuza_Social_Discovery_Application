package com.example.phuza.offline

import kotlinx.coroutines.flow.Flow

class MessagesLocalRepo(
    private val dao: MessageDao
) {
    suspend fun getPendingOutbound(): List<MessageEntity> =
        dao.getPendingOutbound()

    suspend fun upsertMessages(list: List<MessageEntity>) =
        dao.upsert(list)

    suspend fun maxInboundTs(myUid: String): Long? =
        dao.maxInboundTs(myUid)

    fun observeChat(chatId: String): Flow<List<MessageEntity>> =
        dao.observeChat(chatId)

    suspend fun hasMessagesInChat(chatId: String): Boolean =
        dao.countForChat(chatId) > 0

    /**
     * Latest message per chat for this user, but keep the best
     * peerName / peerUsername / peerAvatarBase64 we have in ANY
     * message in that chat.
     */
    suspend fun latestChatsForUser(uid: String): List<MessageEntity> {
        val flat = dao.latestForUserFlat(uid)
        if (flat.isEmpty()) return emptyList()

        return flat
            .groupBy { it.chatId }
            .values
            .map { list ->
                // 1) real latest message (for body + timeSent + outbound/inbound)
                val latest = list.maxByOrNull { it.timeSent }!!

                // 2) pick any message that has meta filled in
                val withMeta = list.firstOrNull {
                    !it.peerName.isNullOrBlank() ||
                            !it.peerUsername.isNullOrBlank() ||
                            !it.peerAvatarBase64.isNullOrBlank()
                }

                if (withMeta == null) {
                    // nothing better, just return latest as-is
                    latest
                } else {
                    // merge meta from withMeta into latest
                    latest.copy(
                        peerUid          = withMeta.peerUid ?: latest.peerUid,
                        peerName         = withMeta.peerName ?: latest.peerName,
                        peerUsername     = withMeta.peerUsername ?: latest.peerUsername,
                        peerAvatarBase64 = withMeta.peerAvatarBase64 ?: latest.peerAvatarBase64
                    )
                }

            }

    }
}
