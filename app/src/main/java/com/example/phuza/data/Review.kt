package com.example.phuza.data

data class Review(
    val id: String = "",
    val place: String = "",
    val description: String = "",
    val rating: Int = 0,
    val imageBase64: String? = null,
    val timestamp: Long = 0L,
    val authorId: String? = null,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val liked: Boolean = false
)
