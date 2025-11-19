package com.example.phuza

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.adapters.BarDashboardAdapter
import com.example.phuza.adapters.FriendReviewAdapter
import com.example.phuza.adapters.FriendsFavBarAdapter
import com.example.phuza.data.*
import com.example.phuza.utils.NotificationUtils
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gestures.gestures
import kotlinx.coroutines.launch
import kotlin.collections.remove
import kotlin.math.abs

// (Android Developers 2025; Mapbox 2025)
class DashboardActivity : BaseActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnNotifications: ImageButton
    private var txtGreeting: TextView? = null
    private var imgAvatar: ImageView? = null

    private var rvFriendReviews: RecyclerView? = null
    private var tvNoFriendReviews: TextView? = null
    private lateinit var friendAdapter: FriendReviewAdapter

    private val firestore = FirebaseFirestore.getInstance() // (Firebase 2019a)
    private var friendshipsListener: ListenerRegistration? = null

    private val activeReviewListeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>() // (Firebase 2019d)

    private var mapSnippetView: com.mapbox.maps.MapView? = null

    private var notifReg: ListenerRegistration? = null
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) } // (Android Developer 2025)

    // Runtime permission launcher for location (Android Developer 2025)
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) centerSnippetOnUser() else Log.w("Dashboard", "Location permission denied")
    }

    private fun requestLocationPermissions() {
        permLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun ensureNotificationPermissionAndStart() {
        // Android 13+ requires explicit POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            // First ask for notifications
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Either lower than 13 or already granted
            if (hasNotificationPermission()) {
                startNotificationsListener() // (Firebase 2019a)
            }
            // Then ask for location
            requestLocationPermissions()
        }
    }

    // --- Notification permission request launcher ---
    // Runtime notifications permission (Android Developer 2024)
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Start Firestore listener if user allowed notifications
            startNotificationsListener() // (Firebase 2019a)
        }
        // In all cases, continue with location permission next
        requestLocationPermissions()
    }

    // --- Notifications ---

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun safeShowNotification(title: String, text: String) {
        if (!hasNotificationPermission()) return
        try {
            NotificationUtils.ensureChannel(this)
            NotificationUtils.show(this, title, text)
        } catch (se: SecurityException) {
            toast("Error: ${se}")
        }
    }

    private fun startNotificationsListener() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val col = db.collection("Notifications").document(uid).collection("items")

        // Live Firestore listener (Firebase 2019a)
        notifReg = col.whereEqualTo("read", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                for (dc in snap.documentChanges) {
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val type = dc.document.getString("type") ?: "notification"
                        val msg  = dc.document.getString("message") ?: ""
                        val title = if (type == "follow_request") "New follow request" else "Follow update"

                        val name = dc.document.getString("fromName") ?: "Someone"
                        val username = dc.document.getString("fromUsername") ?: ""
                        val text = if (username.isNotEmpty()) "$name ($username) $msg" else "$name $msg"

                        safeShowNotification(title, text)
                    }
                }
            }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        Log.d("AUTH", "Dashboard uid=$uid")

        if (uid != null) {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }
                    val token = task.result
                    Log.d("FCM", "FCM token = $token for uid=$uid")

                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .collection("fcmTokens")
                        .document(token)
                        .set(mapOf("createdAt" to System.currentTimeMillis()))
                }
        }

        applyInsets(R.id.root) // (Android Developers 2025)

        findViewById<LinearLayout>(R.id.imPubGolf).setOnClickListener {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUid == null) {
                Toast.makeText(this, "You need to be signed in to play Pub Golf", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val lastGameId = PubGolfPrefs.getLastGameId(this)
            if (!lastGameId.isNullOrBlank()) {
                val i = Intent(this, PubGolfScorecardActivity::class.java).apply {
                    putExtra(PubGolfScorecardActivity.EXTRA_GAME_ID, lastGameId)
                    putExtra(PubGolfScorecardActivity.EXTRA_MY_UID, currentUid)
                }
                startActivity(i)
            } else {
                // Otherwise, create a new game as before
                val i = Intent(this, PubGolfCreateGameActivity::class.java)
                startActivity(i)
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            }
        }

        bottomNav = findViewById(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_dashboard)

        rvFriendReviews = findViewById(R.id.rvFriendReviews)
        tvNoFriendReviews = findViewById(R.id.tvNoFriendReviews)

        txtGreeting = findViewById(R.id.txtGreeting)
        imgAvatar = findViewById(R.id.avatar)
        mapSnippetView = findViewById(R.id.mapSnippetView)

        btnNotifications = findViewById(R.id.btnBell)
        btnNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java)) // (Ahamad 2018)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) // (Ahamad 2018)
        }

        setupSnippetMap() // (Mapbox 2025)

        findViewById<View>(R.id.mapSnippetTapTarget)?.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java)) // (Ahamad 2018)
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right) // (Ahamad 2018)
        }

        bindUserHeader()     // (Firebase 2019d)
        setupAvatarMenu()    // (Ahamad 2018)
        popularBars()        // (Android Developers 2025)
        setupFriendsFavBars()// (Firebase 2019a; Firebase 2019d)
        observeNotificationsBell() // (Firebase 2019a)
        ensureNotificationPermissionAndStart() // (Android Developer 2024)

        friendAdapter = FriendReviewAdapter()
        rvFriendReviews?.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity, LinearLayoutManager.HORIZONTAL, false) // (Android Developers 2025)
            adapter = friendAdapter
        }

        loadFriendsReviews() // (Firebase 2019a; Firebase 2019d)
    }

    private fun loadFriendsReviews() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return // (Firebase 2019b)
        friendshipsListener?.remove()

        // Listen to friendship edges to derive "people I follow" (Firestore) (Firebase 2019a)
        friendshipsListener = firestore.collection("friendships")
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    showNoFriendReviews()
                    detachReviewListeners()
                    return@addSnapshotListener
                }

                val iFollow = mutableListOf<String>()
                for (doc in snap.documents) {
                    val f = doc.toObject(Friendship::class.java) ?: continue
                    if (f.status == "accepted" && f.followerId == uid) {
                        val friendId = if (f.user1Id == uid) f.user2Id else f.user1Id
                        if (friendId.isNotBlank()) iFollow += friendId
                    }
                }

                if (iFollow.isEmpty()) {
                    showNoFriendReviews()
                    detachReviewListeners()
                } else {
                    tvNoFriendReviews?.visibility = View.GONE
                    rvFriendReviews?.visibility = View.VISIBLE
                    fetchAuthorsFromRtdb(iFollow)         // (Firebase 2019d)
                    attachRealtimeReviewsListeners(iFollow) // (Firebase 2019d)
                }
            }
    }

    // Fetch author display info from Realtime Database (Firebase 2019d)
    private fun fetchAuthorsFromRtdb(userIds: List<String>) {
        val rtdb = FirebaseDatabase.getInstance().reference
        userIds.forEach { id ->
            rtdb.child("users").child(id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        val name = s.child("firstName").getValue(String::class.java)
                            ?: s.child("name").getValue(String::class.java)
                        val avatar = s.child("avatar").getValue(String::class.java)
                        friendAdapter.putAuthor(id, name, avatar)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.w("Dashboard", "author fetch cancelled for $id: ${error.message}")
                    }
                })
        }
    }

    // Live-listen to friends' reviews (Firebase 2019d)
    private fun attachRealtimeReviewsListeners(friendIds: List<String>) {
        detachReviewListeners()
        val db = FirebaseDatabase.getInstance().reference
        val merged = mutableListOf<Review>()

        friendIds.forEach { fid ->
            val ref = db.child("users").child(fid).child("reviews")
            val q = ref.orderByChild("timestamp").limitToLast(5)

            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val chunk = s.children.mapNotNull {
                        it.getValue(Review::class.java)?.copy(authorId = fid)
                    }

                    val dedup = (merged + chunk).associateBy { it.id }.values
                        .sortedByDescending { it.timestamp }

                    merged.clear()
                    merged.addAll(dedup)

                    if (merged.isEmpty()) showNoFriendReviews()
                    else {
                        tvNoFriendReviews?.visibility = View.GONE
                        rvFriendReviews?.visibility = View.VISIBLE
                        friendAdapter.submit(merged)
                    }
                }

                override fun onCancelled(e: DatabaseError) {
                    Log.w("Dashboard", "reviews cancelled for $fid: ${e.message}")
                    showNoFriendReviews()
                }
            }

            q.addValueEventListener(l)
            activeReviewListeners += (ref to l)
        }
    }

    private fun detachReviewListeners() {
        activeReviewListeners.forEach { (ref, l) -> ref.removeEventListener(l) }
        activeReviewListeners.clear()
    }

    private fun showNoFriendReviews() {
        rvFriendReviews?.visibility = View.GONE
        tvNoFriendReviews?.visibility = View.VISIBLE
    }

    // Read user's header info from RTDB (Firebase 2019d)
    private fun bindUserHeader() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return // (Firebase 2019b)
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val first = s.child("firstName").getValue(String::class.java) ?: "there"
                    val avatarValue = s.child("avatar").getValue(String::class.java)
                    setHeader(first, avatarValue)
                }
                override fun onCancelled(e: DatabaseError) {
                    Log.e("Dashboard", "DB error: ${e.message}")
                    setHeader("there", null)
                }
            })
    }

    private fun setupAvatarMenu() {
        imgAvatar?.setOnClickListener { v ->
            val popup = PopupMenu(this, v)
            popup.menuInflater.inflate(R.menu.menu_sign_out, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_sign_out -> {
                        FirebaseAuth.getInstance().signOut() // (Firebase 2019b)
                        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.putExtra("skipBiometric", true)
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun setHeader(firstName: String, avatarValue: String?) {
        txtGreeting?.text = "Hi, $firstName"
        when {
            avatarValue.isNullOrBlank() -> imgAvatar?.setImageResource(R.drawable.avatar_no_avatar)
            looksLikeBase64Image(avatarValue) -> {
                val bmp = com.example.phuza.utils.ImageUtils.decodeBase64ToBitmap(avatarValue) // (Anil Kr Mourya 2024)
                if (bmp != null) {
                    imgAvatar?.setImageBitmap(bmp)
                    imgAvatar?.scaleType = ImageView.ScaleType.CENTER_CROP
                } else imgAvatar?.setImageResource(R.drawable.avatar_no_avatar)
            }
            else -> {
                val resId = resources.getIdentifier(avatarValue, "drawable", packageName)
                imgAvatar?.setImageResource(if (resId != 0) resId else R.drawable.avatar_no_avatar)
            }
        }
    }

    private fun looksLikeBase64Image(value: String): Boolean {
        if (value.startsWith("data:image")) return true
        if (value.length < 100) return false
        return value.all { it.isLetterOrDigit() || it in "+/=\n\r" }
    }

    // Minimal, non-interactive Mapbox snippet (Mapbox 2025)
    private fun setupSnippetMap() {
        val mv = mapSnippetView ?: return
        mv.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            mv.gestures.updateSettings {
                scrollEnabled = false
                rotateEnabled = false
                pinchToZoomEnabled = false
                quickZoomEnabled = false
                pitchEnabled = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerSnippetOnUser() {
        fused.lastLocation.addOnSuccessListener { loc ->
            val mv = mapSnippetView ?: return@addOnSuccessListener
            if (loc != null) {
                mv.getMapboxMap().setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(loc.longitude, loc.latitude))
                        .zoom(14.5)
                        .build()
                ) // (Mapbox 2025)
                findViewById<View>(R.id.snippetCenterDot)?.visibility = View.VISIBLE
            }
        }
    }

    // Populate “Popular bars” carousel from Firestore (Firebase 2019a; Android Developers 2025)
    private fun popularBars() {
        val db = FirebaseFirestore.getInstance()
        db.collection("locations")
            .limit(9)
            .get()
            .addOnSuccessListener { snapshot ->
                val bars: List<BarUi> = snapshot.toObjects(BarUi::class.java)

                val recyclerView = findViewById<RecyclerView>(R.id.rvPopularBars)
                val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                recyclerView.layoutManager = layoutManager
                recyclerView.adapter = BarDashboardAdapter(bars)

                val snapHelper = LinearSnapHelper()
                snapHelper.attachToRecyclerView(recyclerView)

                recyclerView.post {
                    recyclerView.scrollToPosition(Int.MAX_VALUE / 2)
                    val view = snapHelper.findSnapView(layoutManager)
                    view?.let {
                        val distance = snapHelper.calculateDistanceToFinalSnap(layoutManager, it)
                        recyclerView.smoothScrollBy(distance?.get(0) ?: 0, distance?.get(1) ?: 0)
                    }
                }

                recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        val center = recyclerView.width / 2
                        for (i in 0 until recyclerView.childCount) {
                            val child = recyclerView.getChildAt(i)
                            val childCenter = (child.left + child.right) / 2
                            val distanceFromCenter = abs(center - childCenter)
                            val scale = 1.0f - (distanceFromCenter.toFloat() / recyclerView.width) * 0.8f
                            child.scaleX = scale.coerceIn(0.3f, 1.0f)
                            child.scaleY = scale.coerceIn(0.3f, 1.0f)
                            val params = child.layoutParams as ViewGroup.MarginLayoutParams
                            params.marginEnd = 20
                            params.marginStart = 20
                            child.layoutParams = params
                        }
                    }
                })
            }
    }

    // Aggregate friends' favourite bars from RTDB + friendships from Firestore (Firebase 2019a; 2019d)
    private fun setupFriendsFavBars() {
        val currentUserId = FirebaseAuth.getInstance().uid ?: return // (Firebase 2019b)
        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        val firestore = FirebaseFirestore.getInstance()

        val rvFriendsFav: RecyclerView = findViewById(R.id.rvFriendsFav)
        val tvNoFriendsFavs: TextView = findViewById(R.id.tvNoFriendsFavs)

        firestore.collection("friendships")
            .get()
            .addOnSuccessListener { snap ->
                val friendIds = mutableListOf<String>()
                for (doc in snap.documents) {
                    val f = doc.toObject(Friendship::class.java) ?: continue
                    if (f.status == "accepted" && f.followerId == currentUserId) {
                        val friendId = if (f.user1Id == currentUserId) f.user2Id else f.user1Id
                        if (friendId.isNotBlank()) friendIds += friendId
                    }
                }

                if (friendIds.isEmpty()) {
                    tvNoFriendsFavs.visibility = View.VISIBLE
                    rvFriendsFav.visibility = View.GONE
                    return@addOnSuccessListener
                }

                usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val barToAvatars = mutableMapOf<String, MutableList<String>>()

                        for (userSnap in snapshot.children) {
                            val uid = userSnap.child("uid").getValue(String::class.java) ?: continue
                            if (uid !in friendIds) continue
                            val avatarValue = userSnap.child("avatar").getValue(String::class.java) ?: continue
                            val favBars = userSnap.child("favoriteBars").children
                                .mapNotNull { it.getValue(String::class.java) }
                            for (barName in favBars) {
                                val list = barToAvatars.getOrPut(barName) { mutableListOf() }
                                if (list.size < 3) list.add(avatarValue)
                            }
                        }

                        val result = barToAvatars.map { (barName, avatars) ->
                            FriendsFavBar(barName = barName, friendAvatars = avatars)
                        }

                        if (result.isEmpty()) {
                            tvNoFriendsFavs.visibility = View.VISIBLE
                            rvFriendsFav.visibility = View.GONE
                        } else {
                            tvNoFriendsFavs.visibility = View.GONE
                            rvFriendsFav.visibility = View.VISIBLE
                            rvFriendsFav.layoutManager = LinearLayoutManager(
                                this@DashboardActivity,
                                LinearLayoutManager.HORIZONTAL,
                                false
                            ) // (Android Developers 2025)
                            rvFriendsFav.adapter = FriendsFavBarAdapter(result)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        tvNoFriendsFavs.visibility = View.VISIBLE
                        rvFriendsFav.visibility = View.GONE
                    }
                })
            }
            .addOnFailureListener {
                tvNoFriendsFavs.visibility = View.VISIBLE
                rvFriendsFav.visibility = View.GONE
            }
    }

    // Badge bell whenever there are unread Firestore notifications (Firebase 2019a)
    private fun observeNotificationsBell() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val bell = findViewById<ImageButton>(R.id.btnBell)

        FirebaseFirestore.getInstance()
            .collection("Notifications")
            .document(uid)
            .collection("items")
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, _ ->
                bell.setImageResource(
                    if (snap != null && !snap.isEmpty)
                        R.drawable.ic_bell_notification
                    else
                        R.drawable.ic_bell
                )
            }
    }


    override fun onDestroy() {
        notifReg?.remove()
        notifReg = null
        super.onDestroy()
    }



    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------------------- BOTTOM NAV ----------------------
    override fun setupBottomNav(nav: BottomNavigationView, selectedId: Int) {
        nav.selectedItemId = selectedId
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_friends -> {
                    startActivity(Intent(this, FriendsActivity::class.java)) // (Ahamad 2018)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
                    true
                }
                R.id.nav_add_review -> {
                    startActivity(Intent(this, AddBarActivity::class.java)) // (Ahamad 2018)
                    overridePendingTransition(R.anim.fade_in_bottom, R.anim.fade_out_bottom)
                    finish()
                    true
                }
                R.id.nav_chats -> {
                    startActivity(Intent(this, ChatsActivity::class.java)) // (Ahamad 2018)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java)) // (Ahamad 2018)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
                    true
                }
                else -> false
            }
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
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
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
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * Mapbox. 2025. “Mapbox Docs”.
 * https://docs.mapbox.com/#search
 * [accessed 16 September 2025].
 */
