package com.example.phuza.data

data class User(
    var uid: String = "",
    var firstName: String = "",
    var username: String = "",
    var email: String = "",
    var createdAt: Long = 0L,
    val avatar: String? = null,
)
