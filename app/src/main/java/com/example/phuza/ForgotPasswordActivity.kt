package com.example.phuza

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException

// (Android Developers 2025)
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnReset: MaterialButton

    private lateinit var successCard: CardView

    private val auth by lazy { FirebaseAuth.getInstance() } // (Firebase 2019b)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // (Android Developers 2025)
        setContentView(R.layout.activity_forgot_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tilEmail = findViewById(R.id.tilEmail)
        etEmail  = findViewById(R.id.etEmail)
        btnReset = findViewById(R.id.btnReset)
        successCard = findViewById(R.id.successCard)

        // Prefill email if passed
        intent.getStringExtra("email")?.let { etEmail.setText(it) } // (Ahamad 2018)

        etEmail.addTextChangedListener {
            val email = it?.toString()?.trim()
            if (isValidEmail(email)) {
                tilEmail.error = null
            }
        }

        btnReset.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()

            if (email.isBlank()) {
                tilEmail.error = "Email is required"
                return@setOnClickListener
            }

            if (!isValidEmail(email)) { // (Android Developers 2025)
                tilEmail.error = "Enter a valid email"
                return@setOnClickListener
            }

            hideKeyboard()

            auth.sendPasswordResetEmail(email) // (Firebase 2019b)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        tilEmail.visibility = View.GONE
                        etEmail.visibility = View.GONE
                        btnReset.visibility = View.GONE
                        findViewById<TextView>(R.id.tvHelper).visibility = View.GONE
                        findViewById<TextView>(R.id.tvTitle).visibility = View.GONE
                        findViewById<ImageView>(R.id.logo_yellow).visibility = View.GONE

                        successCard.visibility = View.VISIBLE

                        // Auto return to login after 2s (Ahamad 2018)
                        Handler(Looper.getMainLooper()).postDelayed({
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }, 2000)

                    } else {
                        val ex = task.exception
                        Log.e("ForgotPassword", "Reset error", ex)
                        tilEmail.error = when (ex) {
                            is FirebaseAuthInvalidUserException ->
                                "No account found with that email"
                            is FirebaseTooManyRequestsException ->
                                "Too many requests. Please try again later"
                            else -> ex?.localizedMessage ?: "Failed to send reset link"
                        }
                    }
                }
        }
    }


    private fun isValidEmail(value: String?) =
        !value.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches() // (Android Developers 2025)

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }
}


/*
 * REFERENCES
 *
 * Ahamad, Musthaq. 2018. “Using Intents and Extras to Pass Data between Activities — Android Beginner’s Guide”.
 * https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0
 * [accessed 28 August 2025].
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 */
