//package com.example.phuza.utils
//
//import android.content.Context
//import androidx.security.crypto.EncryptedSharedPreferences
//import androidx.security.crypto.MasterKey
//
//object SecurePrefs {
//
//    private const val PREF_NAME = "phuza_secure_prefs"
//
//    private fun prefs(context: Context) =
//        EncryptedSharedPreferences.create(
//            context,
//            PREF_NAME,
//            MasterKey.Builder(context)
//                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
//                .build(),
//            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
//            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
//        )
//
//    fun enableBiometrics(context: Context, email: String, password: String) {
//        prefs(context).edit().apply {
//            putString("email", email)
//            putString("password", password)
//            putBoolean("biometric_enabled", true)
//            putBoolean("biometric_declined", false)
//            apply()
//        }
//    }
//    fun disableBiometrics(context: Context) {
//        prefs(context).edit().apply {
//            remove("email")
//            remove("password")
//            putBoolean("biometric_enabled", false)
//            putBoolean("biometric_declined", false)
//            apply()
//        }
//    }
//
//    fun clearBiometrics(context: Context) {
//        prefs(context).edit().apply {
//            putBoolean("biometric_enabled", false)
//            putBoolean("biometric_declined", true) // treat as “I don’t want this”
//            apply()
//        }
//    }
//
//
//    fun enableBiometrics(context: Context, email: String) {
//        prefs(context).edit().putBoolean("biometric_enabled", true)
//            .putString("email", email)
//            .apply()
//    }
//
//
//
//
//    fun isBiometricEnabled(context: Context): Boolean =
//        prefs(context).getBoolean("biometric_enabled", false)
//
//    fun getEmail(context: Context): String? =
//        prefs(context).getString("email", null)
//
//    fun getPassword(context: Context): String? =
//        prefs(context).getString("password", null)
//
//    fun clear(context: Context) {
//        prefs(context).edit().clear().apply()
//    }
//}
package com.example.phuza.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {

    private const val PREF_NAME = "phuza_secure_prefs"

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun enableBiometrics(context: Context, email: String, password: String) {
        prefs(context).edit().apply {
            putString("email", email)
            putString("password", password)
            putBoolean("biometric_enabled", true)
            putBoolean("biometric_declined", false) // user accepted, not declined
            apply()
        }
    }

    fun enableBiometrics(context: Context, email: String) {
        prefs(context).edit().apply {
            putString("email", email)
            putBoolean("biometric_enabled", true)
            putBoolean("biometric_declined", false) // user accepted, not declined
            apply()
        }
    }

    fun disableBiometrics(context: Context) {
        prefs(context).edit().apply {
            remove("email")
            remove("password")
            putBoolean("biometric_enabled", false)
            putBoolean("biometric_declined", true) // user explicitly disabled
            apply()
        }
    }

    fun clearBiometrics(context: Context) {
        prefs(context).edit().apply {
            putBoolean("biometric_enabled", false)
            putBoolean("biometric_declined", true) // treat as “I don’t want this”
            apply()
        }
    }

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean("biometric_enabled", false)

    fun hasDeclinedBiometrics(context: Context): Boolean =
        prefs(context).getBoolean("biometric_declined", false)

    fun getEmail(context: Context): String? =
        prefs(context).getString("email", null)

    fun getPassword(context: Context): String? =
        prefs(context).getString("password", null)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
