package com.example.phuza

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.phuza.databinding.ActivityChangePasswordBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

// (Android Developers 2025)
class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private val auth = FirebaseAuth.getInstance() // (Firebase 2019b)
    private val TAG = "ChangePasswordActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        setupValidationWatchers() // (Android Developers 2025)
    }

    private fun setupListeners(){
        binding.closeBtn.setOnClickListener { finish() }
        binding.btnUpdate.setOnClickListener { attemptPasswordChange() }
    }

    private fun setupValidationWatchers(){

        val watcher = object: TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int){}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int){
                clearErrors()
            }
            override fun afterTextChanged(s: Editable?){}
        }
        binding.etPassword.addTextChangedListener(watcher)
        binding.etNewPassword.addTextChangedListener(watcher)
        binding.etConfirmNewPassword.addTextChangedListener(watcher)
    }

    private fun clearErrors(){

    }

    private fun attemptPasswordChange(){
        val user = auth.currentUser
        val oldPass = binding.etPassword.text?.toString().orEmpty()
        val newPass = binding.etNewPassword.text?.toString().orEmpty()
        val confirmPass = binding.etConfirmNewPassword.text?.toString().orEmpty()

        if(user == null || user.email.isNullOrBlank()){
            Toast.makeText(this, "Authentication error. Please log in again", Toast.LENGTH_LONG).show()
            return
        }

        val credential = EmailAuthProvider.getCredential(user.email!!, oldPass) // (Firebase 2019b)

        setLoading(true)

        // Reauthenticate before sensitive action (Firebase 2019b)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                // Update password after successful reauth (Firebase 2019b)
                user.updatePassword(newPass)
                    .addOnSuccessListener {
                        setLoading(false)
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        Log.e(TAG, "Password update failed: ${e.message}", e)
                        Toast.makeText(this, "Updae failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                val msg = when(e){
                    is FirebaseAuthInvalidCredentialsException -> "The old password entered is incorrect." // (Firebase 2019b)
                    else -> "Re-authentication failed: ${e.localizedMessage}"
                }
                Log.e(TAG, "Re-authenication failed: ${e.message}", e)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
    }

    private fun validateInputs(oldPass: String, newPass: String, confirmPass: String): Boolean{
        if(oldPass.length < 6){
            Toast.makeText(this, "Please enter your current password (min 6 characters).", Toast.LENGTH_SHORT).show()
            return false
        }
        if(newPass.length < 6){
            Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
            return false
        }
        if(newPass != confirmPass){
            Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show()
            return false
        }
        if(oldPass == newPass){
            Toast.makeText(this, "The new password cannot be the same as the old password.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun setLoading(loading: Boolean){
        binding.btnUpdate.isEnabled = !loading
        binding.btnUpdate.text = if(loading) "Updating..." else getString(R.string.update)
    }
}

/*
 * REFERENCES
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 */

