package com.example.phuza

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.phuza.data.Review
import com.example.phuza.databinding.ActivityAddReviewBinding
import com.example.phuza.utils.ImageUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

// (Android Developers 2025)
class AddReviewActivity : BaseActivity() {

    private lateinit var binding: ActivityAddReviewBinding
    private var selectedRating = 0
    private var currentBitmap: Bitmap? = null
    private var isSaving = false
    private var isLiked = false // track heart status

    // Camera preview (Android Developers 2025)
    private val takePicturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> if (bitmap != null) onBitmapChosen(bitmap) else toast("No photo captured") }

    // Gallery picker (Android Developer 2024)
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onImageUriChosen(it) } ?: toast("No image selected") }

    // Camera permission (Android Developers 2025)
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePicturePreview.launch(null)
        else toast("Camera permission is required")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets(binding.main.id)

        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_add_review)

        val tvCancel = findViewById<TextView>(R.id.tvCancel)

        setupStars()
        setupImageActions() // (Android Developer 2024)
        setupHeart()
        setupSave() // (Firebase 2019d)

        tvCancel.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        // Pre-fill the bar name if passed
//        val barName = intent.getStringExtra("bar_name")
//        if (!barName.isNullOrBlank()) {
//            binding.etPlace.setText(barName)
//            binding.etPlace.text(barName.length) // cursor at end
//        }
        val barName = intent.getStringExtra("bar_name")
        if (!barName.isNullOrBlank()) {
            binding.etPlace.text = barName
        }

    }

    // ---------------------- STARS ----------------------
    private fun setupStars() {
        val stars = listOf(binding.rStar1, binding.rStar2, binding.rStar3, binding.rStar4, binding.rStar5)
        stars.forEachIndexed { idx, iv -> iv.setOnClickListener { setRating(idx + 1, stars) } }
    }

    private fun setRating(rating: Int, stars: List<ImageView>) {
        selectedRating = rating
        stars.forEachIndexed { i, iv ->
            val color = if (i < rating) R.color.yellow else R.color.star_inactive
            iv.imageTintList = ContextCompat.getColorStateList(this, color)
        }
    }

    // ---------------------- IMAGE ----------------------
    private fun setupImageActions() {
        binding.btnAddImage.setOnClickListener { showImageChooser() }
        binding.btnAddImage.setOnLongClickListener {
            currentBitmap = null
            binding.btnAddImage.setImageResource(R.drawable.ic_camera)
            binding.btnAddImage.setColorFilter(ContextCompat.getColor(this, R.color.lavender))
            binding.btnAddImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
            toast("Image removed")
            true
        }
    }

    private fun showImageChooser() {
        val options = arrayOf("Take photo", "Choose from gallery")
        AlertDialog.Builder(this)
            .setTitle("Add photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermission.launch(Manifest.permission.CAMERA) // (Android Developers 2025)
                    1 -> pickImage.launch("image/*") // (Android Developer 2024)
                }
            }
            .show()
    }

    private fun onImageUriChosen(uri: Uri) {
        try {
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            onBitmapChosen(bmp)
        } catch (e: Exception) {
            toast("Failed to load image: ${e.message}")
        }
    }

    private fun onBitmapChosen(bitmap: Bitmap) {
        currentBitmap = bitmap
        binding.btnAddImage.setImageBitmap(bitmap)
        binding.btnAddImage.clearColorFilter()
        binding.btnAddImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    // ---------------------- HEART ----------------------
    private fun setupHeart() {
        binding.ivHeart.setOnClickListener {
            isLiked = !isLiked
            val heartIcon = if (isLiked) R.drawable.ic_heart else R.drawable.ic_heart_outline
            binding.ivHeart.setImageResource(heartIcon)
        }
    }

    // ---------------------- SAVE ----------------------
    private fun setupSave() {
        binding.tvSave.setOnClickListener {
            if (isSaving) return@setOnClickListener

            val place = binding.etPlace.text?.toString()?.trim().orEmpty()
            val desc = binding.etDescription.text?.toString()?.trim().orEmpty()

            if (place.isEmpty()) {
                toast("Please enter a place")
                return@setOnClickListener
            }
            if (selectedRating == 0) {
                toast("Please select a rating")
                return@setOnClickListener
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid // (Firebase 2019b)
            if (uid == null) {
                toast("Please sign in again")
                return@setOnClickListener
            }

            // Convert bitmap to Base64 for lightweight storage/transport (Anil Kr Mourya 2024)
            val imageB64 = currentBitmap?.let { ImageUtils.encodeBitmapToBase64(it, quality = 70) } // (Anil Kr Mourya 2024)

            val reviewId = UUID.randomUUID().toString()
            val baseReview = Review(
                id = reviewId,
                place = place,
                description = desc,
                rating = selectedRating,
                imageBase64 = imageB64,
                timestamp = System.currentTimeMillis(),
                authorId = uid,
                authorName = null,
                authorAvatar = null,
                liked = isLiked
            )

            isSaving = true
            setSavingUi(true)

            lifecycleScope.launch {
                try {
                    val userSnap = FirebaseDatabase.getInstance()
                        .reference.child("users").child(uid)
                        .get().await() // (Firebase 2019d)

                    val authorName = userSnap.child("firstName").getValue(String::class.java)
                        ?: userSnap.child("name").getValue(String::class.java)
                    val authorAvatar = userSnap.child("avatar").getValue(String::class.java)

                    val review = baseReview.copy(authorName = authorName, authorAvatar = authorAvatar)

                    val userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid) // (Firebase 2019d)
                    userRef.child("reviews").child(review.id).setValue(review).await()

                    if (isLiked) {
                        userRef.child("likedBars").child(review.place).setValue(true).await()
                    }

                    toast("Review saved")
                    clearFields()
                    val intent = Intent(this@AddReviewActivity, DashboardActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                } catch (e: Exception) {
                    toast("Failed to save: ${e.message}")
                } finally {
                    isSaving = false
                    setSavingUi(false)
                }
            }
        }
    }

    private fun setSavingUi(saving: Boolean) {
        binding.tvSave.isEnabled = !saving
        binding.tvSave.text = if (saving) "Saving…" else "Review Saved"
        binding.tvSave.alpha = if (saving) 0.7f else 1f
    }

    // ---------------------- BOTTOM NAV ----------------------
    override fun setupBottomNav(nav: BottomNavigationView, selectedId: Int) {
        nav.selectedItemId = selectedId
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_friends -> {
                    startActivity(Intent(this, FriendsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_add_review -> true
                R.id.nav_chats -> {
                    startActivity(Intent(this, ChatsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    // ---------------------- HELPERS ----------------------
    private fun clearFields() {
        //binding.etPlace.text?.clear()
        binding.etDescription.text?.clear()

        selectedRating = 0
        val stars = listOf(binding.rStar1, binding.rStar2, binding.rStar3, binding.rStar4, binding.rStar5)
        stars.forEach {
            it.imageTintList = ContextCompat.getColorStateList(this, R.color.star_inactive)
        }

        currentBitmap = null
        binding.btnAddImage.setImageResource(R.drawable.ic_camera)
        binding.btnAddImage.setColorFilter(ContextCompat.getColor(this, R.color.lavender))
        binding.btnAddImage.scaleType = ImageView.ScaleType.CENTER_INSIDE

        isLiked = false
        binding.ivHeart.setImageResource(R.drawable.ic_heart_outline)
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        val intent = Intent(this, DashboardActivity::class.java)
        // You probably don't need NEW_TASK here
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }


    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}

/*
 * REFERENCES
 *
 * Android Developer. 2024. “Grant Partial Access to Photos and Videos”.
 * https://developer.android.com/about/versions/14/changes/partial-photo-video-access
 * [accessed 10 September 2025].
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
 * https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494
 * [accessed 30 September 2025].
 */
