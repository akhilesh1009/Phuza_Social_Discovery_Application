package com.example.phuza

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phuza.adapters.UserAdapter
import com.example.phuza.api.FriendsVMFactory
import com.example.phuza.api.FriendsViewModel
import com.example.phuza.api.RetrofitInstance
import com.example.phuza.api.UiState
import com.example.phuza.data.FriendshipStatus
import com.example.phuza.data.UserDto
import com.example.phuza.databinding.ActivityFriendsBinding
import com.example.phuza.utils.NotificationUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

// (Android Developers 2025)
class FriendsActivity : BaseActivity() {

    private lateinit var binding: ActivityFriendsBinding
    private val vm: FriendsViewModel by viewModels { FriendsVMFactory() } // (Android Developers 2025)

    private lateinit var adapter: UserAdapter
    private var allUsers: List<UserDto> = emptyList()
    private var loadingContainer: FrameLayout? = null
    private var loadingSpinner: ProgressBar? = null
    private var loadingLabel: TextView? = null

    private enum class Mode { FRIENDS, PENDING, DISCOVER }
    private var currentMode: Mode = Mode.FRIENDS


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.main.id) // (Android Developers 2025)

        lifecycleScope.launch {
            wakeServer() // (GeeksforGeeks 2023)
        }

        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_friends) // (Ahamad 2018)

        setupRecycler()   // (Android Developers 2025)
        setupSearch()     // (Android Developers 2025)
        setupModeToggle() // (Android Developers 2025)

        binding.toggleModes.check(binding.btnModeFriends.id)

        buildLoadingOverlay()
        observeVm() // (Android Developers 2025)
        warmUpFriends() // (Firebase 2019d)

    }

    private fun warmUpFriends() {
        if (!vm.hasCache()) {
            showLoading()
            vm.loadUsers(null) // (Firebase 2019d)
        } else {
            vm.loadUsers(null) // (Firebase 2019d)
        }
    }

    private fun setupRecycler() {
        binding.recycler.layoutManager = LinearLayoutManager(this) // (Android Developers 2025)
        adapter = UserAdapter(
            onPrimaryClick = { user, status ->
                val id = user.uid ?: return@UserAdapter
                val incoming = vm.incomingSet.value?.contains(id) == true
                if (incoming && status == FriendshipStatus.requested) return@UserAdapter
                when (status) {
                    null -> vm.sendRequest(id)         // (Firebase 2019d)
                    FriendshipStatus.follow -> vm.unfollow(id) // (Firebase 2019d)
                    FriendshipStatus.requested -> {
                        if (!incoming) vm.cancelRequest(id) // (Firebase 2019d)
                    }
                    FriendshipStatus.following,
                    FriendshipStatus.block -> Unit
                }
            },
            onAccept = { user -> user.uid?.let { vm.acceptRequest(it) } }, // (Firebase 2019d)
            onReject = { user -> user.uid?.let { vm.rejectRequest(it) } }  // (Firebase 2019d)
        )
        binding.recycler.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })
    }

    private fun setupModeToggle() {
        binding.toggleModes.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentMode = when (checkedId) {
                binding.btnModeFriends.id -> Mode.FRIENDS
                binding.btnModePending.id -> Mode.PENDING
                binding.btnModeDiscover.id -> Mode.DISCOVER
                else -> Mode.FRIENDS
            }
            applyFilter()
        }
    }

    private fun observeVm() {
        vm.usersState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> showLoading()
                is UiState.Success -> {
                    hideLoading()
                    allUsers = state.data
                    applyFilter()
                }
                is UiState.Error -> {
                    hideLoading()
                    allUsers = emptyList()
                    applyFilter()
                    toast(state.message)
                }
                else -> Unit
            }
        }
        vm.statusMap.observe(this) { applyFilter() }
        vm.incomingSet.observe(this) { applyFilter() }
    }

    private fun applyFilter() {
        if (!::adapter.isInitialized) return

        val q = binding.etSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val statusMap = vm.statusMap.value ?: emptyMap()

        val filtered = when (currentMode) {
            Mode.FRIENDS -> allUsers.filter { u ->
                (statusMap[u.uid.orEmpty()] == FriendshipStatus.follow)
            }
            Mode.PENDING -> allUsers.filter { u ->
                statusMap[u.uid.orEmpty()] == FriendshipStatus.requested
            }
            Mode.DISCOVER -> allUsers.filter { u ->
                when (statusMap[u.uid.orEmpty()]) {
                    FriendshipStatus.follow,
                    FriendshipStatus.requested -> false
                    else -> true
                }
            }
        }.filter { u ->
            if (q.isEmpty()) true
            else {
                val name = (u.firstName ?: u.name ?: "").lowercase()
                val handle = (u.username ?: "").lowercase()
                name.contains(q) || handle.contains(q)
            }
        }

        adapter.submitUsers(filtered)
        vm.statusMap.value?.let { adapter.submitStatuses(it) }
        vm.incomingSet.value?.let { adapter.submitIncoming(it) }

        val isLoading = vm.usersState.value is UiState.Loading
        if (!isLoading) {
            updateEmptyState(filtered.isEmpty(), q)
        }
    }


    private fun updateEmptyState(isEmpty: Boolean, query: String) {
        if (!isEmpty) {
            binding.emptyView.visibility = View.GONE
            binding.recycler.visibility = View.VISIBLE
            return
        }

        val msg = when (currentMode) {
            Mode.FRIENDS ->
                if (query.isEmpty()) getString(R.string.friends_empty)
                else getString(R.string.friends_empty_search)
            Mode.PENDING ->
                if (query.isEmpty()) getString(R.string.pending_empty)
                else getString(R.string.pending_empty_search)
            Mode.DISCOVER ->
                if (query.isEmpty()) getString(R.string.discover_empty)
                else getString(R.string.discover_empty_search)
        }

        binding.emptyView.text = msg
        binding.emptyView.visibility = View.VISIBLE
        binding.recycler.visibility = View.GONE
    }


    // ------- Loading overlay -------

    private fun buildLoadingOverlay() {
        val root = binding.root as ViewGroup

        loadingContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x66_000000) // semi-transparent black
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }

        loadingSpinner = ProgressBar(this).apply {
            isIndeterminate = true
        }

        loadingLabel = TextView(this).apply {
            text = getString(R.string.loading)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(0, dp(12), 0, 0)
            gravity = Gravity.CENTER
        }

        val column = FrameLayout(this).apply {
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            layoutParams = lp
        }

        val inner = FrameLayout(this)
        inner.addView(loadingSpinner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL
        ))
        inner.addView(loadingLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        ).apply { topMargin = dp(56) })

        column.addView(inner)
        loadingContainer?.addView(column)
        root.addView(loadingContainer)
    }

    private fun showLoading() {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.recycler.visibility = View.GONE
        binding.emptyView.visibility = View.GONE

        binding.toggleModes.isEnabled = false
        binding.etSearch.isEnabled = false
    }

    private fun hideLoading() {
        binding.loadingContainer.visibility = View.GONE

        // Re-enable user interaction
        binding.toggleModes.isEnabled = true
        binding.etSearch.isEnabled = true
    }

    private fun dp(px: Int): Int =
        (px * resources.displayMetrics.density).toInt()

    // Bottom nav helper for BaseActivity
    override fun setupBottomNav(nav: BottomNavigationView, selectedId: Int) {
        nav.selectedItemId = selectedId
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard   -> { startActivity(Intent(this, DashboardActivity::class.java));
                    overridePendingTransition(R.anim.slide_out_right, R.anim.slide_in_left)
                    finish();
                    true }

                R.id.nav_friends     -> true
                R.id.nav_add_review  -> { startActivity(Intent(this, AddBarActivity::class.java));
                    overridePendingTransition(R.anim.fade_in_bottom, R.anim.fade_out_bottom)
                    finish();
                    true }
                R.id.nav_chats -> { startActivity(Intent(this, ChatsActivity::class.java));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish();
                    true  }
                R.id.nav_profile     -> { startActivity(Intent(this, ProfileActivity::class.java));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    true }
                else -> false
            }
        }
    }

    private suspend fun wakeServer() {
        try {
            RetrofitInstance.api.health() // (GeeksforGeeks 2023)
        } catch (e: Exception) {
            toast(getString(R.string.error_prefix, e.message ?: "Unknown"))
        }
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
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/*
 * REFERENCES
 *
 * Ahamad, Musthaq. 2018. "Using Intents and Extras to Pass Data between Activities — Android Beginner's Guide".
 * https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0
 * [accessed 28 August 2025].
 *
 * Android Developer. 2024. "Grant Partial Access to Photos and Videos".
 * https://developer.android.com/about/versions/14/changes/partial-photo-video-access
 * [accessed 10 September 2025].
 *
 * Android Developer. 2025. "Request Location Permissions | Sensors and Location".
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 *
 * Android Developers. 2025. "Create Dynamic Lists with RecyclerView".
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Firebase. 2019a. "Cloud Firestore | Firebase".
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. "Firebase Authentication | Firebase".
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019c. "Firebase Cloud Messaging | Firebase".
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. "Firebase Realtime Database".
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * GeeksforGeeks. 2017. "How to Use Glide Image Loader Library in Android Apps?"
 * https://www.geeksforgeeks.org/android/image-loading-caching-library-android-set-2/
 * [accessed 30 September 2025].
 *
 * GeeksforGeeks. 2020. "SimpleAdapter in Android with Example".
 * https://www.geeksforgeeks.org/android/simpleadapter-in-android-with-example/
 * [accessed 19 August 2025].
 *
 * GeeksforGeeks. 2023. "How to GET Data from API Using Retrofit Library in Android?"
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 * Nainal. 2019. "Add Multiple SHA for Same OAuth for Google SignIn Android".
 * https://stackoverflow.com/questions/55142027/add-multiple-sha-for-same-oauth-for-google-signin-android
 * [accessed 11 August 2025].
 */
