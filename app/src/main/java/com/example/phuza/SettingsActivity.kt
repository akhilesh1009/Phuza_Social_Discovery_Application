package com.example.phuza

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.phuza.databinding.ActivitySettingsBinding
import com.example.phuza.utils.ImageUtils
import com.example.phuza.utils.AvatarUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.phuza.utils.SecurePrefs

// (Android Developers 2025)
class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val firestore = FirebaseFirestore.getInstance() // (Firebase 2019a)
    private val auth = FirebaseAuth.getInstance()            // (Firebase 2019b)
    private var userListener: ListenerRegistration? = null

    // For Gallery Upload
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var requestMediaPermsLauncher: ActivityResultLauncher<Array<String>>

    private var avatarDbRef: com.google.firebase.database.DatabaseReference? = null // (Firebase 2019d)
    private var avatarDbListener: com.google.firebase.database.ValueEventListener? = null // (Firebase 2019d)

    // (Android Developers 2025)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialise view binding
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editAvatarContainer.setOnClickListener {
            showAvatarOptionsBottomSheet()
        }

        binding.switchBiometric.isChecked = SecurePrefs.isBiometricEnabled(this)

        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->

            val email = auth.currentUser?.email
            if (email == null) {
                toast("Cannot change biometric settings. Please log in.")
                binding.switchBiometric.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                // Check if device supports biometrics
                val biometricManager = BiometricManager.from(this)
                when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        showBiometricPrompt(email)
                    }
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                        toast("Biometric authentication is not set up on this device.")
                        binding.switchBiometric.isChecked = false
                    }
                }
            } else {
                // Disable biometrics directly
                SecurePrefs.disableBiometrics(this)
                toast("Fingerprint login disabled")
            }
        }

        // Setup image pickers for gallery upload (copied from Onboarding1Activity)
        setupImagePickers() // (Android Developer 2024)

        // Setup UI elements
        setupActionBar() // (Android Developers 2025)
        setupListItemTitles() // (Android Developers 2025)
        setupOnClickListeners() // (Ahamad 2018)

        // Real-time profile observation (Firebase 2019d)
        observeUserProfile()
    }

    private fun setupImagePickers() {
        // (Android Developer 2024)
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadAvatarFromUri(uri)
        }

        requestMediaPermsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted = result.values.any { it }
            if (granted) pickImageLauncher.launch("image/*") else toast("Permission required to choose a photo")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
    }

    // Observe Firebase Realtime Database (Firebase 2019d)
    private fun observeUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        avatarDbRef?.let { ref ->
            avatarDbListener?.let { ref.removeEventListener(it) }
        }

        val ref = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("avatar")

        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val avatarStr = snapshot.getValue(String::class.java)
                displayAvatar(avatarStr, binding.avatar)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // fallback visual
                binding.avatar.setImageResource(R.drawable.avatar_no_avatar)
            }
        }

        ref.addValueEventListener(listener)
        avatarDbRef = ref
        avatarDbListener = listener
    }

    // (GeeksforGeeks 2017; Anil Kr Mourya 2024)
    private fun displayAvatar(avatarStr: String?, imageView: ImageView) {
        val mappedRes = avatarStr?.let { key ->
            AvatarUtil.avatarList.firstOrNull { it.first == key }?.second
        }
        when {
            mappedRes != null -> {
                imageView.setImageResource(mappedRes)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            }
            !avatarStr.isNullOrBlank() -> {
                val base64 = avatarStr.substringAfter(",", avatarStr)
                val bmp = ImageUtils.decodeBase64ToBitmap(base64) // (Anil Kr Mourya 2024)
                if (bmp != null) {
                    imageView.setImageBitmap(bmp)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    Glide.with(this) // (GeeksforGeeks 2017)
                        .load(avatarStr)
                        .placeholder(R.drawable.avatar_no_avatar)
                        .error(R.drawable.avatar_no_avatar)
                        .into(imageView)
                }
            }
            else -> {
                imageView.setImageResource(R.drawable.avatar_no_avatar)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
    }

    // (Ahamad 2018)
    private fun showAvatarSelectionBottomSheet() {
        AvatarUtil.showAvatarSelector(this) { selectedAvatarKey ->
            saveSelectedAvatarToFirebase(selectedAvatarKey)
        }
    }

    // Firestore merge and RTDB sync (Firebase 2019a; Firebase 2019d)
    private fun saveSelectedAvatarToFirebase(avatarKey: String) {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val firestoreRef = firestore.collection("users").document(userId)
        val rtdbRef = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("users").child(userId)
        val updateMap = mapOf("avatar" to avatarKey)

        firestoreRef.set(updateMap, SetOptions.merge()).addOnSuccessListener {
            rtdbRef.child("avatar").setValue(avatarKey).addOnSuccessListener {
                Toast.makeText(this, "Avatar updated successfully!", Toast.LENGTH_SHORT).show()
                displayAvatar(avatarKey, binding.avatar)
            }.addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Avatar updated to settings (Firestore), but failed to update Profile (RTDB). Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error updating avatar in Firestore: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // (Android Developers 2025)
    private fun setupListItemTitles() {
        setSettingTitle(
            binding.settingsChangePassword.root,
            getString(R.string.setting_change_password)
        )

        setSettingTitle(
            binding.settingsLanguagePrefs.root,
            getString(R.string.setting_language_prefs)
        )
    }

    private fun setSettingTitle(itemView: android.view.View, title: String) {
        itemView.findViewById<TextView>(R.id.settings_item_title)?.text = title
    }

    // (Android Developers 2025)
    private fun setupActionBar() {
        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    // (Android Developer 2024; Ahamad 2018)
    private fun setupOnClickListeners() {
//        binding.btnChooseAvatar.setOnClickListener {
//            showAvatarSelectionBottomSheet()
//        }

//        binding.btnUploadGallery.setOnClickListener {
//            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
//                arrayOf(Manifest.permission.READ_MEDIA_IMAGES) // (Android Developer 2024)
//            } else {
//                @Suppress("DEPRECATION")
//                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
//            }
//            requestMediaPermsLauncher.launch(perms)
//        }

        binding.settingsChangePassword.root.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java)) // (Ahamad 2018)
        }

        binding.settingsLanguagePrefs.root.setOnClickListener {
            startActivity(Intent(this, LanguagePreferenceActivity::class.java))
        }
    }

    private fun showAvatarOptionsBottomSheet() {
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_avatar_options, null)
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        bottomSheetView.findViewById<Button>(R.id.btnChooseAvatarSheet).setOnClickListener {
            bottomSheetDialog.dismiss()
            showAvatarSelectionBottomSheet() // Your existing function
        }

        bottomSheetView.findViewById<Button>(R.id.btnUploadGallerySheet).setOnClickListener {
            bottomSheetDialog.dismiss()
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                @Suppress("DEPRECATION")
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestMediaPermsLauncher.launch(perms)
        }

        bottomSheetDialog.show()
    }

    // (Anil Kr Mourya 2024)
    private fun loadAvatarFromUri(uri: android.net.Uri) {
        try {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(contentResolver, uri)
                )
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            val maxSide = 640
            val scaled = scaleBitmapIfNeeded(bitmap, maxSide)
            val pickedImageBase64 = ImageUtils.encodeBitmapToBase64(scaled, quality = 70)
            saveSelectedAvatarToFirebase(pickedImageBase64)
        } catch (e: Exception) {
            toast("Failed to load image: ${e.message}")
        }
    }

    private fun showBiometricPrompt(email: String) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    SecurePrefs.enableBiometrics(this@SettingsActivity, email)
                    toast("Fingerprint login enabled")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    toast("Authentication error: $errString")
                    binding.switchBiometric.isChecked = false
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    toast("Authentication failed")
                    binding.switchBiometric.isChecked = false
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm your identity")
            .setSubtitle("Authenticate to enable fingerprint login")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }


    // (GeeksforGeeks 2017)
    private fun scaleBitmapIfNeeded(src: android.graphics.Bitmap, maxSide: Int): android.graphics.Bitmap {
        val w = src.width
        val h = src.height
        val largest = maxOf(w, h)
        if (largest <= maxSide) return src
        val scale = maxSide.toFloat() / largest
        val nw = (w * scale).toInt()
        val nh = (h * scale).toInt()
        return android.graphics.Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
