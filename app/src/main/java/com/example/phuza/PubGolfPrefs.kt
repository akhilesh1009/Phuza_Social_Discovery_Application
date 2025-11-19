package com.example.phuza

import android.content.Context

object PubGolfPrefs {
    private const val PREFS_NAME = "pubgolf_prefs"
    private const val KEY_LAST_GAME_ID = "last_game_id"

    fun setLastGameId(context: Context, gameId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (gameId == null) remove(KEY_LAST_GAME_ID)
            else putString(KEY_LAST_GAME_ID, gameId)
        }.apply()
    }

    fun getLastGameId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_GAME_ID, null)
    }
}
