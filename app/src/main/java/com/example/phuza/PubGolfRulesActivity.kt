package com.example.phuza

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class PubGolfRulesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pub_golf_rules)

        val topBar = findViewById<MaterialToolbar>(R.id.topBarPubGolfRules)
        topBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
