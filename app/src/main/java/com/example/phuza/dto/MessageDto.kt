package com.example.phuza.dto

data class MessageDto(
    val id: String? = null,
    val fromUid: String,
    val toUid: String,
    val body: String,
    val clientId: String? = null,
    val status: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)
