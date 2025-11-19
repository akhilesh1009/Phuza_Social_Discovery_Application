package com.example.phuza

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.phuza.data.User
import com.example.phuza.databinding.ActivityRegisterBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

// (Android Developers 2025)
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    private val TAG = "RegisterActivity"

    // Firebase (default instance from google-services.json)
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }               // (Firebase 2019b)
    private val dbRoot: DatabaseReference by lazy { FirebaseDatabase.getInstance().reference } // (Firebase 2019d)

    // (Android Developers 2025)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvLoginLink.setOnClickListener { finish() }       // (Ahamad 2018)
        binding.btnRegister.setOnClickListener { attemptRegister() } // (Ahamad 2018)
    }

    // Method that validates and stores the users registration details
    // (Android Developers 2025)
    private fun attemptRegister() {
        clearErrors()

        val first = binding.etFirstName.text?.toString()?.trim().orEmpty()
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val pass = binding.etPassword.text?.toString()?.trim().orEmpty()
        //val pass2 = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()

        var ok = true
        if (first.isEmpty()) { binding.tilFirst.error = "Required"; ok = false }
        if (username.isEmpty()) { binding.tilUser.error = "Required"; ok = false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.tilEmail.error = "Enter a valid email"; ok = false } // (Android Developers 2025)
        if (pass.length < 6) { binding.tilPassword.error = "Min 6 characters"; ok = false }
        if (!ok) return

        setLoading(true)

        // Create account with Firebase Auth (Firebase 2019b)
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid
                if (uid.isNullOrEmpty()) {
                    setLoading(false)
                    snack("Registration failed: no UID.")
                    Log.e(TAG, "UID missing after createUser")
                    return@addOnSuccessListener
                }

                writeProfile(uid, first, username, email) // (Firebase 2019d)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                val msg = when (e) {
                    is FirebaseAuthUserCollisionException -> "An account with this email already exists."
                    is FirebaseAuthWeakPasswordException -> "Password is too weak."
                    else -> e.localizedMessage ?: "Registration failed."
                }
                snack(msg)
                Log.e(TAG, "Auth createUser failed", e)
            }
    }

    // Write initial profile to Firebase Realtime Database (Firebase 2019d)
    private fun writeProfile(
        uid: String,
        firstName: String,
        username: String,
        email: String
    ) {
        // Client object
        val profile = User(
            uid = uid,
            firstName = firstName,
            username = username,
            email = email,
            createdAt = 0L
        )

        val updates = hashMapOf<String, Any>(
            "users/$uid/uid" to profile.uid,
            "users/$uid/firstName" to profile.firstName,
            "users/$uid/username" to profile.username,
            "users/$uid/email" to profile.email,
            "users/$uid/createdAt" to ServerValue.TIMESTAMP, // (Firebase 2019d)
            "users/$uid/onboardingComplete" to false
        )

        dbRoot.updateChildren(updates)
            .addOnSuccessListener {
                setLoading(false)
                goToHome() // (Ahamad 2018)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                snack(e.localizedMessage ?: "Failed saving profile.")
                Log.e(TAG, "Profile write failed at /users/$uid", e)
                auth.currentUser?.delete()
            }
    }

    // (Android Developers 2025)
    private fun clearErrors() {
        binding.tilFirst.error = null
        binding.tilUser.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
    }

    // (GeeksforGeeks 2020)
    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnRegister.text = if (loading) "Creating…" else "Lets Phuza"
    }

    private fun snack(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    // Navigate after registration (Ahamad 2018)
    private fun goToHome() {
        startActivity(Intent(this, Onboarding1Activity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

/*
 * REFERENCES
 *
 * Ahamad, Musthaq. 2018. “Using Intents and Extras to Pass Data between Activities — Android Beginner’s Guide”.
 * https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0
 * [accessed 28 August 2025].
 *
 * Ananth.k. 2023. “Kotlin — SerializedName Annotation”.
 * https://medium.com/@ananthkvn2016/kotlin-serializedname-annotation-2ad375f83371
 * [accessed 19 September 2025].
 *
 * Android Developer. 2024. “Grant Partial Access to Photos and Videos”.
 * https://developer.android.com/about/versions/14/changes/partial-photo-video-access
 * [accessed 10 September 2025].
 *
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Android Knowledge. 2023a. “Bottom Navigation Bar in Android Studio Using Java | Explanation”.
 * https://www.youtube.com/watch?v=0x5kmLY16qE
 * [accessed 20 September 2023].
 *
 * Android Knowledge. 2023b. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”.
 * https://www.youtube.com/watch?v=oGyQMBKPuNY
 * [accessed 21 September 2025].
 *
 * Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
 * https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494
 * [accessed 30 September 2025].
 *
 * Firebase. 2019a. “Cloud Firestore | Firebase”.
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * GeeksforGeeks. 2017. “How to Use Glide Image Loader Library in Android Apps?”
 * https://www.geeksforgeeks.org/android/image-loading-caching-library-android-set-2/
 * [accessed 30 September 2025].
 *
 * GeeksforGeeks. 2020. “SimpleAdapter in Android with Example”.
 * https://www.geeksforgeeks.org/android/simpleadapter-in-android-with-example/
 * [accessed 19 August 2025].
 *
 * GeeksforGeeks. 2021. “State ProgressBar in Android”.
 * https://www.geeksforgeeks.org/android/state-progressbar-in-android/
 * [accessed 22 September 2025].
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 * Mapbox. 2025. “Mapbox Docs”.
 * https://docs.mapbox.com/#search
 * [accessed 16 September 2025].
 *
 * Nainal. 2019. “Add Multiple SHA for Same OAuth for Google SignIn Android”.
 * https://stackoverflow.com/questions/55142027/add-multiple-sha-for-same-oauth-for-google-signin-android
 * [accessed 11 August 2025].
 */
