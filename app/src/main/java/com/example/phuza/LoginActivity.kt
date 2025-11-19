package com.example.phuza

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.phuza.databinding.ActivityLoginBinding
import com.example.phuza.utils.SecurePrefs
import com.google.android.material.snackbar.Snackbar
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch

// (Android Developers 2025)
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }          // (Firebase 2019b)
    private val db by lazy { FirebaseDatabase.getInstance().reference }            // (Firebase 2019d)

    // (Android Developers 2025)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Nav
        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java)) // (Ahamad 2018)
        }
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java)) // (Ahamad 2018)
        }

        // Email/password login (Firebase 2019b)
        binding.btnLogin.setOnClickListener { attemptEmailLogin() }
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptEmailLogin(); true } else false
        }

        // Google SSO (Firebase 2019b; Nainal 2019)
        binding.btnGoogleLogin.setOnClickListener { signInWithGoogle() }

        val skipBiometric = intent.getBooleanExtra("skipBiometric", false)
        if (SecurePrefs.isBiometricEnabled(this) && !skipBiometric) {
            showBiometricPrompt()
        }

    }

    override fun onStart() {
        super.onStart()
        auth.currentUser?.let { routeBasedOnOnboarding(it.uid) } // (Firebase 2019b; Firebase 2019d)
    }

    private fun showBiometricPrompt() {
        val prompt = androidx.biometric.BiometricPrompt(
            this,
            androidx.core.content.ContextCompat.getMainExecutor(this),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    val email = SecurePrefs.getEmail(this@LoginActivity)
                    val password = SecurePrefs.getPassword(this@LoginActivity)

                    if (email != null && password != null) {
                        showLoading(true)
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener {
                                routeBasedOnOnboarding(auth.currentUser!!.uid)
                            }
                            .addOnFailureListener {
                                showLoading(false)
                                Snackbar.make(binding.root, "Biometric login failed.", Snackbar.LENGTH_LONG).show()
                            }
                    }
                }
            }
        )

        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Login with fingerprint")
            .setSubtitle("Authenticate to login")
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(info)
    }


    // ---------------- Email/Password ----------------
    private fun attemptEmailLogin() {
        clearErrors()

        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (!isEmailValid(email)) {
            binding.emailLayout.error = "Enter a valid email" // (Android Developers 2025)
            hasError = true
        }
        if (password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            hasError = true
        }
        if (hasError) return

        showLoading(true)
        auth.signInWithEmailAndPassword(email, password) // (Firebase 2019b)
            .addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val uid = auth.currentUser!!.uid

            val biometricsEnabled = SecurePrefs.isBiometricEnabled(this)
            val biometricsDeclined = SecurePrefs.hasDeclinedBiometrics(this)

            if (!biometricsEnabled && !biometricsDeclined) {
                // User has not made a decision yet → ask once
//                androidx.appcompat.app.AlertDialog.Builder(this)
//                    .setTitle("Enable Fingerprint Login?")
//                    .setMessage("Would you like to use fingerprint to log in next time?")
//                    .setPositiveButton("Yes") { _, _ ->
//                        SecurePrefs.enableBiometrics(this, email, password)
//                        routeBasedOnOnboarding(uid)
//                    }
//                    .setNegativeButton("No") { _, _ ->
//                        // Mark as declined so we don't ask again
//                        SecurePrefs.clearBiometrics(this) // sets declined = true in our new code
//                        routeBasedOnOnboarding(uid)
//                    }
//                    .show()
                // Inflate the custom biometric dialog layout
                val dialogView = layoutInflater.inflate(R.layout.dialog_biometric, null)
                val dialog = AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create()

                // "Yes" button action
                dialogView.findViewById<Button>(R.id.btnYes).setOnClickListener {
                    SecurePrefs.enableBiometrics(this, email, password)
                    dialog.dismiss()
                    routeBasedOnOnboarding(uid)
                }

                // "No" button action
                dialogView.findViewById<Button>(R.id.btnNo).setOnClickListener {
                    SecurePrefs.clearBiometrics(this)
                    dialog.dismiss()
                    routeBasedOnOnboarding(uid)
                }

                // Show the dialog
                dialog.show()

            } else {
                // Either already enabled OR user has declined – just continue
                routeBasedOnOnboarding(uid)
            }
        } else {
                showLoading(false)
                val msg = when (val e = task.exception) {
                    is FirebaseAuthInvalidUserException -> "No account found for this email."
                    is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
                    else -> e?.localizedMessage ?: "Login failed. Try again."
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    // ---------------- Google SSO ----------------
    private fun signInWithGoogle() {
        val serverClientId = getString(R.string.default_web_client_id) // (Nainal 2019)

        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val cm = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                val result = cm.getCredential(this@LoginActivity, request) // (Firebase 2019b)
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                when (e) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException -> { /* user canceled */ }
                    else -> {
                        Snackbar.make(binding.root, "Google sign-in failed (${e::class.java.simpleName})", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun handleGoogleCredential(credential: Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCred = GoogleAuthProvider.getCredential(idToken, null) // (Firebase 2019b)
            showLoading(true)
            auth.signInWithCredential(firebaseCred)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        showLoading(false)
                        Snackbar.make(
                            binding.root,
                            task.exception?.localizedMessage ?: "Google sign-in failed.",
                            Snackbar.LENGTH_LONG
                        ).show()
                        return@addOnCompleteListener
                    }
                    ensureUserProfileThenRoute() // (Firebase 2019d)
                }
        } else {
            Snackbar.make(binding.root, "Selected credential is not a Google account.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun ensureUserProfileThenRoute() {
        val fUser = auth.currentUser ?: run { showLoading(false); return }
        val uid = fUser.uid
        val email = fUser.email.orEmpty()
        val displayName = fUser.displayName.orEmpty()

        val ref = db.child("users").child(uid)
        ref.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                routeBasedOnOnboarding(uid) // (Firebase 2019d)
                return@addOnSuccessListener
            }

            val firstName = displayName.trim().takeIf { it.isNotEmpty() }?.substringBefore(' ')
                ?: email.substringBefore('@')
            val username = email.substringBefore('@').lowercase()

            val updates = hashMapOf<String, Any>(
                "users/$uid/uid" to uid,
                "users/$uid/firstName" to firstName,
                "users/$uid/username" to username,
                "users/$uid/email" to email,
                "users/$uid/createdAt" to ServerValue.TIMESTAMP,     // (Firebase 2019d)
                "users/$uid/onboardingComplete" to false
            )

            db.updateChildren(updates) // (Firebase 2019d)
                .addOnSuccessListener {
                    showLoading(false)
                    routeToOnboarding()
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Snackbar.make(binding.root, e.localizedMessage ?: "Failed to save profile.", Snackbar.LENGTH_LONG).show()
                }
        }.addOnFailureListener { e ->
            showLoading(false)
            Snackbar.make(binding.root, e.localizedMessage ?: "Could not verify account.", Snackbar.LENGTH_LONG).show()
        }
    }

    // ---------------- Routing helpers ----------------
    private fun routeBasedOnOnboarding(uid: String) {
        db.child("users").child(uid).child("onboardingComplete").get() // (Firebase 2019d)
            .addOnSuccessListener { snap ->
                showLoading(false)
                val done = snap.getValue(Boolean::class.java) == true
                if (done) routeToDashboard() else routeToOnboarding() // (Ahamad 2018)
            }
            .addOnFailureListener {
                showLoading(false)
                routeToOnboarding()
            }
    }

    private fun routeToOnboarding() {
        startActivity(Intent(this, Onboarding1Activity::class.java))
        finish()
        overridePendingTransition(R.anim.fade_in_bottom, R.anim.fade_out_bottom) // (Ahamad 2018)
    }

    private fun routeToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
        overridePendingTransition(R.anim.fade_in_bottom, R.anim.fade_out_bottom) // (Ahamad 2018)
    }

    // ---------------- UI helpers ----------------
    private fun isEmailValid(email: String?): Boolean =
        !email.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches() // (Android Developers 2025)

    private fun showLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Logging in…" else "Login"
        binding.btnGoogleLogin.isEnabled = !loading
    }

    private fun clearErrors() {
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
    }
}

/*
 * REFERENCES
 *
 * Ahamad, Musthaq. 2018. “Using Intents and Extras to Pass Data between Activities — Android Beginner’s Guide”.
 * https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0
 * [accessed 28 August 2025].
 *
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
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
 * Nainal. 2019. “Add Multiple SHA for Same OAuth for Google SignIn Android”.
 * https://stackoverflow.com/questions/55142027/add-multiple-sha-for-same-oauth-for-google-signin-android
 * [accessed 11 August 2025].
 */
