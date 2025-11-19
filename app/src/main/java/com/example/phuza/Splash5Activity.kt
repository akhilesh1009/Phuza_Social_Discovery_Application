package com.example.phuza

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class Splash5Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash5)

        routeFromSplash()
    }

    private fun routeFromSplash() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            // Not signed in: go to Login
            goTo(LoginActivity::class.java)
            return
        }
        val ref = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(user.uid)
            .child("onboardingComplete")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val done = snapshot.getValue(Boolean::class.java) == true
                if (done) goTo(DashboardActivity::class.java)
                else goTo(Onboarding1Activity::class.java)
            }

            override fun onCancelled(error: DatabaseError) {
                goTo(Onboarding1Activity::class.java)
            }
        })
    }

    private fun goTo(cls: Class<*>) {
        startActivity(Intent(this, cls))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
