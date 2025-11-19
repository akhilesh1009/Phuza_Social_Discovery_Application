package com.example.phuza

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phuza.adapters.NotificationsAdapter
import com.example.phuza.data.AppNotification
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// (Android Developers 2025)
class NotificationsActivity : AppCompatActivity() {

    // Firebase Auth + Firestore (Firebase 2019b; Firebase 2019a)
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: NotificationsAdapter
    private var reg: com.google.firebase.firestore.ListenerRegistration? = null

    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    // (Android Developers 2025)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish() // (Ahamad 2018)
        }

        markAllRead() // mark unread as read at entry (Firebase 2019a)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Notifications"

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications)
        val empty = findViewById<View>(R.id.empty)

        adapter = NotificationsAdapter { n ->
            markAsRead(n) // (Firebase 2019a)
        }
        rv.layoutManager = LinearLayoutManager(this) // (Android Developers 2025)
        rv.adapter = adapter

        // Live query: most recent first (Firebase 2019a)
        val uid = auth.currentUser?.uid ?: return
        val col = db.collection("Notifications").document(uid).collection("items")

        reg?.remove()
        reg = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val items = snap.documents.map { AppNotification.from(it) }
                adapter.submitList(items)
                if (items.isEmpty()) {
                    empty.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                } else {
                    empty.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                }
            }
    }

    override fun onDestroy() {
        reg?.remove()
        reg = null
        super.onDestroy()
    }

    // Options menu (Android Developers 2025)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Mark all read")
        return true
    }

    // Home/back + bulk mark-read (Ahamad 2018; Firebase 2019a)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish(); return true
        }
        if (item.itemId == 1) {
            markAllRead()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Single doc update (Firebase 2019a)
    private fun markAsRead(n: AppNotification) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("Notifications").document(uid)
            .collection("items").document(n.id)
            .update("read", true)
    }

    // Batch mark-all-read using a query + write batch (Firebase 2019a)
    private fun markAllRead() {
        val uid = auth.currentUser?.uid ?: return
        val col = db.collection("Notifications").document(uid).collection("items")
        uiScope.launch(Dispatchers.IO) {
            val snap = col.whereEqualTo("read", false).get().awaitOrNull() ?: return@launch
            val batch = db.batch()
            snap.documents.forEach { batch.update(it.reference, "read", true) }
            batch.commit()
        }
    }
}

// tiny await helper (no coroutines-ktx import requirement change) (Android Developers 2025)
private fun <T> Task<T>.awaitOrNull(): T? =
    try { com.google.android.gms.tasks.Tasks.await(this); this.result } catch (_: Exception) { null }


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
