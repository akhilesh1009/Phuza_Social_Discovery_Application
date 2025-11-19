package com.example.phuza

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.phuza.databinding.ActivityNotificationsPreferencesBinding
import com.example.phuza.utils.NotificationUtils
import androidx.core.content.ContextCompat

class NotificationsPreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsPreferencesBinding

    private val PREFS_FILE = "NotificationPrefs"
    private val KEY_PUSH_MASTER = "push_master_enabled"
    private val KEY_EMAIL_MASTER = "email_master_enabled"

    private val sharedPrefs by lazy {
        getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        loadAndSetupSwitches()

        binding.saveButton.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        reflectSystemPushStatus()
    }

    private fun setupActionBar(){
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun loadAndSetupSwitches(){

        val isPushEnabled = sharedPrefs.getBoolean(KEY_PUSH_MASTER, false)
        binding.switchPushMaster.isChecked = isPushEnabled

        binding.switchPushMaster.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean(KEY_PUSH_MASTER, isChecked).apply()

            Toast.makeText(this, "Push notifications ${if(isChecked) "enabled" else "disabled"}",
                Toast.LENGTH_SHORT).show()
        }

        val isEmailEnabled = sharedPrefs.getBoolean(KEY_EMAIL_MASTER, false)
        binding.switchEmailMaster.isChecked = isEmailEnabled

        binding.switchEmailMaster.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean(KEY_EMAIL_MASTER, isChecked).apply()

            Toast.makeText(this, "Email notifications ${if(isChecked) "enabled" else "disabled"}",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun reflectSystemPushStatus(){
        val isSystemEnabled = NotificationUtils.canPostNotifications(this)
        binding.switchPushMaster.isChecked = isSystemEnabled

        updateLocalPushPreference(isSystemEnabled)

        if(!isSystemEnabled){
            binding.switchPushMaster.setThumbResource(0)
            Toast.makeText(this, "Master push notifications controled by System Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateLocalPushPreference(isEnabled: Boolean){
        sharedPrefs.edit().putBoolean(KEY_PUSH_MASTER, isEnabled).apply()
    }

    private fun openAppNotificationSettings(){
        val intent = when{
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            }
            else -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            }
        }
        try{
            startActivity(intent)
        }
        catch (e: Exception){
            Toast.makeText(this, "Could not open system settings.", Toast.LENGTH_SHORT).show()
        }
    }
}