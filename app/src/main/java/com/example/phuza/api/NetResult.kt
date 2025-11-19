package com.example.phuza.api

sealed class NetResult<out T> {
    data class Ok<T>(val data: T) : NetResult<T>()
    data class Err(val message: String, val code: Int? = null) : NetResult<Nothing>()
}
