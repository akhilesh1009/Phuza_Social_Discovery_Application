package com.example.phuza.offline

import android.content.Context
/**
 * Aggregates local Room access for workers / viewmodels.
 */
class LocalRepos(ctx: Context) {

    private val db = AppDatabase.getInstance(ctx.applicationContext)

    val messages: MessagesLocalRepo by lazy {
        MessagesLocalRepo(db.messageDao())
    }
}
