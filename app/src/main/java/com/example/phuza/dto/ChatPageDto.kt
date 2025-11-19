package com.example.phuza.dto

import com.example.phuza.api.Meta

data class ChatPageDto(
    val success: Boolean,
    val chatId: String,
    val messages: List<MessageDto>,
    val nextAfter: Long?,            // server cursor to continue forward
    val meta: Meta?                  // e.g., { order: "asc", limit: 100 }
)