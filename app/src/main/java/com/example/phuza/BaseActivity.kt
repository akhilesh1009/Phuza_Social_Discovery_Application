package com.example.phuza

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.phuza.utils.LocaleHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

// (Android Developers 2025)
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val localizedContext = LocaleHelper.onAttach(newBase)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // (Android Developers 2025)
    }

    protected fun applyInsets(rootId: Int) {
        val root = findViewById<View>(rootId)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sys.top) // bottom handled by layout’s own padding/card
            WindowInsetsCompat.CONSUMED
        }
    }

    protected open fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        // highlight the current tab
        if (bottomNav.selectedItemId != selectedItemId) {
            bottomNav.selectedItemId = selectedItemId
        }

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true

            when (item.itemId) {
                R.id.nav_dashboard   -> launchTop(DashboardActivity::class.java)
                R.id.nav_friends     -> launchTop(FriendsActivity::class.java)
                R.id.nav_add_review  -> launchTop(AddBarActivity::class.java)
                R.id.nav_chats -> launchTop(ChatsActivity::class.java)
                R.id.nav_profile     -> launchTop(ProfileActivity::class.java)
                else -> false
            }
        }
    }

    private fun launchTop(target: Class<*>) : Boolean {
        val intent = Intent(this, target).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        startActivity(intent) // (Ahamad 2018)
        overridePendingTransition(0, 0) // no animation between tabs (Ahamad 2018)
        finish()
        return true
    }
}

/*
 * REFERENCES
 *
 * Ahamad, Musthaq. 2018. “Using Intents and Extras to Pass Data between Activities — Android Beginner’s Guide”.
 * https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0
 * [accessed 28 August 2025].
 *
 * Android Developers. 2025. “Edge-to-Edge Display | Android UI Guidelines”.
 * https://developer.android.com/develop/ui/views/layout/edge-to-edge
 * [accessed 20 September 2025].
 *
 * Android Developers. 2025. “Navigation and Task Management”.
 * https://developer.android.com/guide/components/activities/tasks-and-back-stack
 * [accessed 22 September 2025].
 *
 * Android Developers. 2025. “Material Design Components — Bottom Navigation”.
 * https://developer.android.com/develop/ui/views/components/bottom-navigation-view
 * [accessed 21 September 2025].
 */
