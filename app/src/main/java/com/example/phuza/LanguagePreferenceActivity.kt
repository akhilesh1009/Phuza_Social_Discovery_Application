package com.example.phuza

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.phuza.databinding.ActivityLanguagePreferenceBinding
import com.example.phuza.utils.LocaleHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LanguagePreferenceActivity : BaseActivity() {

    private lateinit var binding: ActivityLanguagePreferenceBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var selectedLanguageCode: String = "en" // default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLanguagePreferenceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupUI()
    }

    private fun setupUI() {
        val optionEnglish = findViewById<RelativeLayout>(R.id.option_english)
        val optionIsiZulu = findViewById<RelativeLayout>(R.id.option_isizulu)
        val optionAfrikaans = findViewById<RelativeLayout>(R.id.option_afrikaans)

        val checkEnglish = findViewById<ImageView>(R.id.check_english)
        val checkIsiZulu = findViewById<ImageView>(R.id.check_isizulu)
        val checkAfrikaans = findViewById<ImageView>(R.id.check_afrikaans)

        val saveButton = findViewById<TextView>(R.id.save_button)
        val closeButton = findViewById<View>(R.id.close_btn)

        // Load current saved language to set initial state
        val currentLang = LocaleHelper.getSavedLanguage(this) ?: LocaleHelper.getDefaultLanguage()
        selectedLanguageCode = currentLang
        updateCheckmarks(currentLang, checkEnglish, checkIsiZulu, checkAfrikaans)

        optionEnglish.setOnClickListener {
            selectedLanguageCode = "en"
            updateCheckmarks("en", checkEnglish, checkIsiZulu, checkAfrikaans)
        }

        optionIsiZulu.setOnClickListener {
            selectedLanguageCode = "zu"
            updateCheckmarks("zu", checkEnglish, checkIsiZulu, checkAfrikaans)
        }

        optionAfrikaans.setOnClickListener {
            selectedLanguageCode = "af"
            updateCheckmarks("af", checkEnglish, checkIsiZulu, checkAfrikaans)
        }

//        saveButton.setOnClickListener {
//            LocaleHelper.setNewLocale(this, selectedLanguageCode)
//            recreate() // force text to reload
//        }
        saveButton.setOnClickListener {
            onSaveClicked()
        }


        closeButton.setOnClickListener {
            finish()
        }
    }

    private fun updateCheckmarks(
        languageCode: String,
        checkEnglish: ImageView,
        checkIsiZulu: ImageView,
        checkAfrikaans: ImageView
    ) {
        checkEnglish.visibility = if (languageCode == "en") View.VISIBLE else View.GONE
        checkIsiZulu.visibility = if (languageCode == "zu") View.VISIBLE else View.GONE
        checkAfrikaans.visibility = if (languageCode == "af") View.VISIBLE else View.GONE
    }

    private fun onSaveClicked() {
        // Save in SharedPreferences + update configuration
        LocaleHelper.setNewLocale(this, selectedLanguageCode)

        val user = auth.currentUser
        if (user != null) {
            val userId = user.uid
            val userDocRef = db.collection("users").document(userId)
            userDocRef.update("language", selectedLanguageCode)
        }

        Toast.makeText(this, getString(R.string.language_updated), Toast.LENGTH_SHORT).show()

        val intent = Intent(this, DashboardActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        }
        startActivity(intent)
        finish()
    }
}
