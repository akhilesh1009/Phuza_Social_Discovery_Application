package com.example.phuza

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.phuza.api.RetrofitInstance
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Wake server on a background thread
        lifecycleScope.launch {
            wakeServer()
        }

        // Delay then go to Splash1
        window.decorView.postDelayed({
            startActivity(Intent(this, Splash1Activity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 130)
    }
}

private suspend fun wakeServer() {
    try {
        RetrofitInstance.api.health()
    } catch (e: Exception) {
    }
}


