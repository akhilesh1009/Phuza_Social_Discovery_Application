package com.example.phuza

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView

class InvitationsActivity : BaseActivity() {

    private lateinit var tvComingSoon: TextView
    private lateinit var dotAnimator: ValueAnimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invitations)
        applyInsets(R.id.main)

        tvComingSoon = findViewById(R.id.tvComingSoon)
        startDotAnimation()

        // Bottom navigation setup
        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNav)
        //setupBottomNav(bottomNav, R.id.nav_invitations)
    }
    private fun startDotAnimation() {
        val baseText = "Coming Soon"
        val maxDots = 4

        dotAnimator = ValueAnimator.ofInt(0, maxDots)
        dotAnimator.duration = 1000 // total cycle duration in ms
        dotAnimator.repeatCount = ValueAnimator.INFINITE
        dotAnimator.addUpdateListener { animation ->
            val dots = animation.animatedValue as Int
            tvComingSoon.text = baseText + ".".repeat(dots)
        }
        dotAnimator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        dotAnimator.cancel()
    }


    override fun setupBottomNav(nav: BottomNavigationView, selectedId: Int) {
        nav.selectedItemId = selectedId
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(R.anim.slide_out_right, R.anim.slide_in_left)
                    finish()
                    true
                }
                R.id.nav_friends -> {
                    startActivity(Intent(this, FriendsActivity::class.java))
                    overridePendingTransition(R.anim.slide_out_right, R.anim.slide_in_left)
                    finish()
                    true
                }
                R.id.nav_add_review -> {
                    startActivity(Intent(this, AddReviewActivity::class.java))
                    overridePendingTransition(R.anim.fade_in_bottom, R.anim.fade_out_bottom)
                    finish()
                    true
                }
               // R.id.nav_invitations -> true
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    true
                }
                else -> false
            }
        }
    }
}
