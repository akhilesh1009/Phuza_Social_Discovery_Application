// com.example.phuza.repo.MessagesRepository.kt
package com.example.phuza.repo

import com.example.phuza.api.MessagesApi
import com.example.phuza.api.NetResult
import com.example.phuza.api.SendMessagePayload
import com.example.phuza.dto.ChatPageDto
import com.example.phuza.dto.MessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class MessagesRepository(private val api: MessagesApi) {

    suspend fun send(fromUid: String, toUid: String, body: String, clientId: String? = null)
            : NetResult<MessageDto> = safe {
        api.send(
            SendMessagePayload(
                fromUid = fromUid,
                toUid = toUid,
                body = body,
                clientId = clientId
            )
        )
    }

    suspend fun since(uid: String?, sinceEpochMs: Long): NetResult<List<MessageDto>> =
        safe { api.listSince(sinceEpochMs, uid) }
    suspend fun withPeer(uid: String, peerId: String, limit: Int? = null)
            : NetResult<List<MessageDto>> = safe {
        api.listWithPeer(peerId = peerId, uid = uid, limit = limit)
    }

    suspend fun byChat(uid: String, chatId: String, limit: Int? = 100, after: Long? = null)
            : NetResult<ChatPageDto> = safe {
        api.listByChat(chatId = chatId, uid = uid, limit = limit, afterEpochMs = after)
    }

    private suspend fun <T> safe(block: suspend () -> Response<T>): NetResult<T> =
        withContext(Dispatchers.IO) {
            try {
                val r = block()
                if (r.isSuccessful) {
                    r.body()?.let { NetResult.Ok(it) }
                        ?: NetResult.Err("Empty body", r.code())
                } else {
                    NetResult.Err(
                        r.errorBody()?.string().orEmpty()
                            .ifBlank { "HTTP ${r.code()}" },
                        r.code()
                    )
                }
            } catch (t: Throwable) {
                NetResult.Err(t.message ?: "Network error")
            }
        }
}
