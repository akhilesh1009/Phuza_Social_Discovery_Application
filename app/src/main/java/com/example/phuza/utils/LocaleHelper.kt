package com.example.phuza.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun onAttach(context: Context): Context {
        val lang = getSavedLanguage(context) ?: getDefaultLanguage()
        return setLocale(context, lang)
    }

    fun getDefaultLanguage(): String {
        return "en" // fallback language code
    }

    fun getSavedLanguage(context: Context): String? {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_LANGUAGE, null)
    }

    fun setNewLocale(context: Context, language: String): Context {
        saveLanguage(context, language)
        return setLocale(context, language)
    }

    private fun saveLanguage(context: Context, language: String) {
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    @SuppressLint("ApplySharedPref")
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun setLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
}
/*
 * REFERENCES
 *
 * GeeksforGeeks. 2025. “How to Change the Whole App Language in Android Programmatically?”. [online]
 * https://www.geeksforgeeks.org/android/how-to-change-the-whole-app-language-in-android-programmatically/
 * [Accessed 16 November 2025].*/