package com.example.phuza

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phuza.adapters.VisitedBarsAdapter
import com.example.phuza.data.Review
import com.example.phuza.databinding.ActivityVisitedBarsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

// (Android Developers 2025)
class VisitedBarsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVisitedBarsBinding
    private val auth = FirebaseAuth.getInstance() // (Firebase 2019b)
    private val rtdb = FirebaseDatabase.getInstance().reference // (Firebase 2019d)

    private lateinit var adapter: VisitedBarsAdapter

    // (Ahamad 2018; Android Developers 2025)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisitedBarsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle back navigation
        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        fetchUserReviews()
    }

    // (Android Developers 2025; GeeksforGeeks 2020)
    private fun setupRecyclerView() {
        adapter = VisitedBarsAdapter()
        binding.rvVisitedBars.layoutManager = LinearLayoutManager(this)
        binding.rvVisitedBars.adapter = adapter
    }

    // Fetching Firebase Realtime Database data (Firebase 2019d)
    private fun fetchUserReviews() {
        val uid = auth.currentUser?.uid ?: return

        rtdb.child("users").child(uid).child("reviews")
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                // Handle success response (Firebase 2019d)
                override fun onDataChange(snapshot: DataSnapshot) {
                    val reviews = snapshot.children.mapNotNull {
                        try {
                            it.getValue(Review::class.java)
                        } catch (e: Exception) {
                            Log.e("VisitedBars", "Failed to map review: ${it.key}, Error: ${e.message}")
                            null
                        }
                    }.sortedByDescending { it.timestamp }

                    adapter.submitList(reviews)

                    // Manage empty or populated UI state (GeeksforGeeks 2020)
                    if (reviews.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvVisitedBars.visibility = View.GONE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvVisitedBars.visibility = View.VISIBLE
                    }
                }

                // Handle cancelled response (Firebase 2019d)
                override fun onCancelled(error: DatabaseError) {
                    binding.tvEmptyState.text = "Error loading bars."
                    binding.tvEmptyState.visibility = View.VISIBLE
                }
            })
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
