package com.example.phuza

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.adapters.BarAdapter
import com.example.phuza.adapters.DrinksAdapter
import com.example.phuza.adapters.UserAdapter
import com.example.phuza.api.Coordinates
import com.example.phuza.api.DiscoverPubsRequest
import com.example.phuza.api.FriendsVMFactory
import com.example.phuza.api.FriendsViewModel
import com.example.phuza.api.RetrofitInstance
import com.example.phuza.api.StartingPoint
import com.example.phuza.api.UiState
import com.example.phuza.data.Drink
import com.example.phuza.data.FriendshipStatus
import com.example.phuza.databinding.ActivityOnboarding1Binding
import com.example.phuza.managers.OnboardingValidation
import com.example.phuza.utils.*
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// (Android Developers 2025)
class Onboarding1Activity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboarding1Binding

    // Step flow
    private var currentStep = 0
    private val totalSteps = 6
    private lateinit var sections: List<LinearLayout>

    // DOB (Step 1)
    private lateinit var monthDropdown: AutoCompleteTextView
    private lateinit var dayDropdown: AutoCompleteTextView
    private lateinit var yearDropdown: AutoCompleteTextView
    private lateinit var monthLayout: TextInputLayout
    private lateinit var dayLayout: TextInputLayout
    private lateinit var yearLayout: TextInputLayout

    // Location (Step 2)
    private var userLat: Double? = null
    private var userLon: Double? = null
    private val locationUtil = LocationUtil()
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var isFindingLocation = false

    // Bars (Step 3)
    private lateinit var recyclerViewBars: RecyclerView
    private lateinit var barAdapter: BarAdapter
    private var barsLoaded = false
    private var barsSearchJob: Job? = null

    // Drinks (Step 4)
    private lateinit var drinksRecycler: RecyclerView
    private lateinit var chipGroup: ChipGroup
    private lateinit var drinksAdapter: DrinksAdapter
    private val drinkList = mutableListOf<Drink>()

    // Friends (Step 5)
    private lateinit var friendsAdapter: UserAdapter
    private lateinit var friendsRecycler: RecyclerView
    private val friendsVm: FriendsViewModel by viewModels { FriendsVMFactory() }
    private var friendsSearchJob: Job? = null
    private val selectedFriendUids = mutableSetOf<String>()

    // Avatar (Step 6)
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var requestMediaPermsLauncher: ActivityResultLauncher<Array<String>>
    private var pickedImageBase64: String? = null
    private var selectedAvatarKey: String? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOnboarding1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        warmUpPubsApi() // (GeeksforGeeks 2023)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Image pickers (Step 6) (Android Developer 2024)
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadAvatarFromUri(uri) // (Anil Kr Mourya 2024)
        }
        requestMediaPermsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted = result.values.any { it }
            if (granted) pickImageLauncher.launch("image/*") else toast("Permission required to choose a photo")
        }

        // Sections
        sections = listOf(
            binding.sectionStep1,
            binding.sectionStep2,
            binding.sectionStep3,
            binding.sectionStep4,
            binding.sectionStep5,
            binding.sectionStep6
        )
        showStep(0)

        setupNextButton()        // (Ahamad 2018)
        setupSkipButton()        // (Ahamad 2018)
        setupDateOfBirthDropdowns() // (Android Developers 2025)
        setupLocationLaunchers() // (Android Developer 2025)
        setupLocationButton()    // (Android Developer 2025)
        setupBarsRecycler()      // (Android Developers 2025)
        setupDrinksRecycler()    // (Android Developers 2025)
        setupFriendsRecycler()   // (Android Developers 2025)
        setupFriendsSearch()     // (GeeksforGeeks 2020)
        setupBarsSearch()        // (GeeksforGeeks 2020)
        setupAvatarSelector()    // (Android Developer 2024)

        friendsVm.usersState.observe(this) { state ->
            when (state) {
                is UiState.Success -> {
                    hideFriendsLoading()
                    applyFriendsFilter()
                }
                is UiState.Error -> {
                    hideFriendsLoading()
                    friendsAdapter.submitUsers(emptyList())
                    toast(state.message)
                }
                is UiState.Loading -> showFriendsLoading()
                UiState.Idle -> Unit
            }
        }

        friendsVm.statusMap.observe(this) { map ->
            friendsAdapter.submitStatuses(map) // (Firebase 2019d)
            applyFriendsFilter()
        }
        friendsVm.incomingSet.observe(this) { set ->
            friendsAdapter.submitIncoming(set) // (Firebase 2019d)
            applyFriendsFilter()
        }
    }

    // Recompute filtered friends (Android Developers 2025)
    private fun applyFriendsFilter() {
        val state = friendsVm.usersState.value as? UiState.Success ?: return
        val statuses = friendsVm.statusMap.value ?: emptyMap()
        val incoming = friendsVm.incomingSet.value ?: emptySet()

        val filtered = state.data.filter { u ->
            val key = u.uid ?: u.id ?: u.username.orEmpty()
            val s = statuses[key]
            val isIncoming = incoming.contains(key)

            when {
                isIncoming -> false
                s == null -> true
                s == FriendshipStatus.requested -> true
                else -> false
            }
        }

        friendsAdapter.submitUsers(filtered)
    }

    // ----------------- Warm-up -----------------
    // Pre-warm the backend so the first real call is fast. (GeeksforGeeks 2023)
    private fun warmUpPubsApi() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val req = DiscoverPubsRequest(
                    origin = StartingPoint(
                        name = "Boot",
                        address = "App start",
                        coordinates = Coordinates(longitude = 18.4241, latitude = -33.9249)
                    ),
                    numberOfPubs = 3,
                    radiusMeters = 1000
                )
                RetrofitInstance.api.discoverPubs(req)
            } catch (_: Exception) { /* ignore warm-up errors */ }
        }
    }

    // ----------------- Step Flow -----------------
    private fun setupNextButton() {
        val validator = OnboardingValidation()

        binding.btnNext.setOnClickListener {
            if (currentStep < totalSteps - 1) {
                when (currentStep) {
                    // STEP 1: DOB
                    0 -> {
                        val day = dayDropdown.text.toString()
                        val month = monthDropdown.text.toString()
                        val year = yearDropdown.text.toString()
                        if (!validator.dateOfBirthValidation(day, month, year, this)) return@setOnClickListener
                        DateOfBirthUtil.saveDob(this, day, month, year)
                    }
                    // STEP 2: LOCATION
                    1 -> {
                        if (isFindingLocation) {
                            toast("Still finding your location, please wait...")
                            return@setOnClickListener
                        }
                        if (userLat == null || userLon == null) {
                            toast("Please enable location before continuing.")
                            return@setOnClickListener
                        }
                    }
                    // STEP 3: BARS
                    2 -> {
                        if (!validator.selectBarsValidation(barAdapter.getSelectedBars(), this)) return@setOnClickListener
                        saveBars() // (Firebase 2019d)
                    }
                    // STEP 4: DRINKS
                    3 -> {
                        if (!validator.selectDrinksValidation(drinksAdapter.getSelectedDrink(), this)) return@setOnClickListener
                        saveDrink() // (Firebase 2019d)
                    }
                    4 -> {
                        // Commit friend requests now (Firebase 2019d)
                        for (uid in selectedFriendUids) {
                            friendsVm.sendRequest(uid)
                        }
                        selectedFriendUids.clear()
                    }
                    // STEP 6: AVATAR
                    5 -> {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
                        val db = FirebaseDatabase.getInstance().getReference("users").child(uid)

                        when {
                            pickedImageBase64 != null -> db.child("avatar").setValue(pickedImageBase64) // (Anil Kr Mourya 2024; Firebase 2019d)
                            selectedAvatarKey != null -> db.child("avatar").setValue(selectedAvatarKey) // (Firebase 2019d)
                            else -> toast("No avatar selected, you can set one later")
                        }
                        pickedImageBase64 = null
                        selectedAvatarKey = null
                    }

                    else -> Unit
                }

                currentStep++
                showStep(currentStep, forward = true)

            } else {
                markOnboardingCompleteAndFinish() // (Firebase 2019d)
            }
        }
    }

    private fun setupSkipButton() {
        binding.tvSkip.setOnClickListener {
            if (currentStep >= totalSteps - 1) {
                markOnboardingCompleteAndFinish()
            } else {
                when (currentStep) {
                    4 -> {
                        selectedFriendUids.clear()
                    }
                    5 -> {
                        pickedImageBase64 = null
                        selectedAvatarKey = null
                        binding.avatarView.setImageDrawable(null)
                        binding.avatarView.visibility = View.GONE
                        binding.cameraIcon.visibility = View.VISIBLE
                    }
                }

                currentStep++
                showStep(currentStep)
            }
        }

    }

    private fun markOnboardingCompleteAndFinish() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val ref = FirebaseDatabase.getInstance().getReference("users").child(uid).child("onboardingComplete")
        ref.setValue(true).addOnCompleteListener {
            startActivity(Intent(this, OnboardingSuccessActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }
    }

    private fun showStep(stepIndex: Int, forward: Boolean = true) {
        sections.forEachIndexed { index, section ->
            if (index == stepIndex) {
                val animIn = if (forward) R.anim.slide_in_right else R.anim.slide_in_left
                section.startAnimation(AnimationUtils.loadAnimation(this, animIn))
                section.visibility = View.VISIBLE
            } else if (section.visibility == View.VISIBLE) {
                val animOut = if (forward) R.anim.slide_out_left else R.anim.slide_out_right
                section.startAnimation(AnimationUtils.loadAnimation(this, animOut))
                section.visibility = View.GONE
            }
        }

        binding.progressBar.progress = ((stepIndex.toFloat() / (totalSteps - 1)) * 100).toInt()

        binding.tvSkip.visibility = if (stepIndex >= 4) View.VISIBLE else View.GONE

        // Load bars once (Step 3 index = 2)
        if (stepIndex == 2 && !barsLoaded) {
            val lat = userLat
            val lon = userLon
            if (lat == null || lon == null) {
                binding.sectionStep2.visibility = View.VISIBLE
                binding.sectionStep3.visibility = View.GONE
                currentStep = 1
                toast("Please enable location to see nearby bars.")
                return
            }
            if (!::barAdapter.isInitialized) {
                barAdapter = BarAdapter(userLat = lat, userLon = lon) {  } // (Android Developers 2025)
                recyclerViewBars.adapter = barAdapter
            }
            binding.progressLoadingBars.visibility = View.VISIBLE
            binding.tvLoadingBars.visibility = View.VISIBLE

            LoadBarsUtil.startLoadingBars(
                activity = this,
                adapter = barAdapter,
                userLat = lat,
                userLon = lon,
                progressBar = binding.progressLoadingBars,
                loadingText = binding.tvLoadingBars,
                query = binding.searchBarInput.text?.toString()
            )
            barsLoaded = true
        }

        if (stepIndex == 4) {
            val q = binding.searchFriendInput.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }
            friendsVm.loadUsers(q) // (Firebase 2019d)
        }
    }

    // ----------------- DOB -----------------
    private fun setupDateOfBirthDropdowns() {
        monthDropdown = findViewById(R.id.dropMonthDate)
        dayDropdown = findViewById(R.id.dropdownDate)
        yearDropdown = findViewById(R.id.dropdownYear)

        monthLayout = findViewById(R.id.dropdownMonthLayout)
        dayLayout = findViewById(R.id.dropdownDateLayout)
        yearLayout = findViewById(R.id.dropdownYearLayout)

        DateOfBirthUtil.setupDropdown(this, monthDropdown, DateOfBirthUtil.Months)
        DateOfBirthUtil.setupDropdown(this, yearDropdown, DateOfBirthUtil.getYears(minYear = 1984, minAge = 18))
        DateOfBirthUtil.setupDayDropdown(this, dayDropdown, monthDropdown, yearDropdown)

        monthDropdown.setOnItemClickListener { _, _, _, _ ->
            DateOfBirthUtil.updateDays(this, dayDropdown, monthDropdown, yearDropdown)
        }
        yearDropdown.setOnItemClickListener { _, _, _, _ ->
            DateOfBirthUtil.updateDays(this, dayDropdown, monthDropdown, yearDropdown)
        }

        DateOfBirthUtil.setupDropdownArrow(this, monthDropdown, monthLayout)
        DateOfBirthUtil.setupDropdownArrow(this, dayDropdown, dayLayout)
        DateOfBirthUtil.setupDropdownArrow(this, yearDropdown, yearLayout)
    }

    // ----------------- Location -----------------
    @SuppressLint("MissingPermission")
    private fun setupLocationLaunchers() {
        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                locationUtil.checkLocationSettings(this, locationSettingsLauncher) {
                    locationUtil.getAccurateLocation(this, ::onLocationEnabled) { msg ->
                        toast(msg)
                        resetLocationButton()
                    }
                }
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    toast("Please enable location permission in Settings")
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.fromParts("package", packageName, null)
                    startActivity(intent)
                } else {
                    toast("Location permission is required to find nearby bars.")
                }
                resetLocationButton()
            }
        }

        locationSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                locationUtil.getAccurateLocation(this, ::onLocationEnabled) { msg ->
                    toast(msg)
                    resetLocationButton()
                }
            } else {
                toast("Please enable location services.")
                resetLocationButton()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationButton() {
        binding.btnGetLocation.setOnClickListener {
            isFindingLocation = true
            binding.btnGetLocation.isEnabled = false
            binding.btnGetLocation.text = "Finding your location..."

            locationUtil.requestPermissions(this, requestPermissionsLauncher) { // (Android Developer 2025)
                locationUtil.checkLocationSettings(this, locationSettingsLauncher) {
                    locationUtil.getAccurateLocation(
                        this,
                        { lat, lon ->
                            isFindingLocation = false
                            resetLocationButton()
                            onLocationEnabled(lat, lon)
                        },
                        { msg ->
                            isFindingLocation = false
                            toast(msg)
                            resetLocationButton()
                        }
                    )
                }
            }
        }
    }

    private fun onLocationEnabled(lat: Double, lon: Double) {
        userLat = lat
        userLon = lon
        saveLocation(lat, lon) // (Android Developer 2025)

        if (currentStep == 1) {
            currentStep = 2
            showStep(currentStep)
        } else if (currentStep == 2 && !barsLoaded) {
            showStep(2)
        }
    }

    private fun resetLocationButton() {
        binding.btnGetLocation.isEnabled = true
        binding.btnGetLocation.text = "Enable Location"
    }

    private fun saveLocation(lat: Double, lon: Double) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val city = addresses[0].locality
                val suburb = addresses[0].subLocality
                val area = suburb ?: city ?: "Unknown Area"
                FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    FirebaseDatabase.getInstance().getReference("users")
                        .child(uid).child("location").setValue(area) // (Firebase 2019d)
                }
            }
        } catch (_: Exception) {}
    }

    // ----------------- Bars (Step 3) -----------------
    private fun setupBarsRecycler() {
        recyclerViewBars = findViewById(R.id.recyclerViewBars)
        val gridLayoutManager = GridLayoutManager(this, 2)
        recyclerViewBars.layoutManager = gridLayoutManager
        recyclerViewBars.addItemDecoration(GridSpacingItemDecoration(2, 30))
    }

    class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: android.view.View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % spanCount
            outRect.left = column * spacing / spanCount
            outRect.right = spacing - (column + 1) * spacing / spanCount
            if (position >= spanCount) outRect.top = spacing
        }
    }

    private fun setupBarsSearch() {
        binding.searchBarInput.addTextChangedListener { text ->
            barsSearchJob?.cancel()
            barsSearchJob = lifecycleScope.launch {
                delay(250) // debounce (GeeksforGeeks 2020)
                val q = text?.toString()
                if (::barAdapter.isInitialized && userLat != null && userLon != null) {
                    LoadBarsUtil.startLoadingBars(
                        activity = this@Onboarding1Activity,
                        adapter = barAdapter,
                        userLat = userLat!!,
                        userLon = userLon!!,
                        progressBar = binding.progressLoadingBars,
                        loadingText = binding.tvLoadingBars,
                        query = q
                    )
                }
            }
        }
    }

    private fun saveBars() {
        val selectedBars = barAdapter.getSelectedBars()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val favNames = selectedBars.map { it.name }
        FirebaseDatabase.getInstance().getReference("users")
            .child(userId)
            .child("favoriteBars")
            .setValue(favNames) // (Firebase 2019d)
    }

    // ----------------- Drinks (Step 4) -----------------
    private fun setupDrinksRecycler() {
        drinksRecycler = findViewById(R.id.recyclerViewDrinks)
        chipGroup = findViewById(R.id.chipGroup)
        drinksRecycler.layoutManager = LinearLayoutManager(this)
        drinksAdapter = DrinksAdapter(emptyList())
        drinksRecycler.adapter = drinksAdapter

        DrinksUtil.fetchDrinks(this, chipGroup, drinksAdapter) { fetchedDrinks ->
            drinkList.clear()
            drinkList.addAll(fetchedDrinks)
        }
    }

    private fun saveDrink() {
        val selectedDrink = drinksAdapter.getSelectedDrink()
        if (selectedDrink == null) return

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("users")
            .child(userId)
            .child("favoriteDrink")
            .setValue(selectedDrink.name) // (Firebase 2019d)
    }

    // ----------------- Friends (Step 5) -----------------
    private fun setupFriendsRecycler() {
        friendsRecycler = binding.recyclerViewFriends
        friendsRecycler.layoutManager = LinearLayoutManager(this)

        friendsAdapter = UserAdapter(
            onPrimaryClick = { user, status ->
                val otherUid = user.uid ?: return@UserAdapter

                when (status) {
                    null -> { selectedFriendUids.add(otherUid) }
                    FriendshipStatus.requested -> { selectedFriendUids.remove(otherUid) }
                    FriendshipStatus.follow,
                    FriendshipStatus.following,
                    FriendshipStatus.block -> Unit
                }
            },
            onAccept = {  },
            onReject = {  }
        )

        friendsRecycler.adapter = friendsAdapter
    }

    private fun setupFriendsSearch() {
        binding.searchFriendInput.addTextChangedListener { text ->
            friendsSearchJob?.cancel()
            friendsSearchJob = lifecycleScope.launch {
                delay(300) // debounce (GeeksforGeeks 2020)
                val q = text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }
                friendsVm.loadUsers(q) // (Firebase 2019d)
            }
        }
    }

    // ----------------- Avatar (Step 6) -----------------
    private fun setupAvatarSelector() {
        binding.btnChooseAvatar.setOnClickListener {
            AvatarUtil.showAvatarSelector(this) { avatarKey ->
                selectedAvatarKey = avatarKey
                pickedImageBase64 = null // reset gallery if choosing key avatar

                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@showAvatarSelector

                if (avatarKey == "none") {
                    // Clear avatar
                    selectedAvatarKey = null
                    binding.avatarView.setImageDrawable(null)
                    binding.avatarView.visibility = View.GONE
                    binding.cameraIcon.visibility = View.VISIBLE

                    FirebaseDatabase.getInstance().reference
                        .child("users").child(uid).child("avatar")
                        .removeValue() // (Firebase 2019d)
                } else {
                    val drawableRes = AvatarUtil.avatarList.find { it.first == avatarKey }?.second
                    if (drawableRes != null) {
                        binding.avatarView.setImageDrawable(
                            ContextCompat.getDrawable(this, drawableRes)
                        )
                        binding.avatarView.visibility = View.VISIBLE
                        binding.cameraIcon.visibility = View.GONE
                    }

                    // Save selected key avatar
                    FirebaseDatabase.getInstance().reference
                        .child("users").child(uid).child("avatar")
                        .setValue(avatarKey) // (Firebase 2019d)
                }
            }
        }

        binding.btnUploadGallery.setOnClickListener {
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES) // (Android Developer 2024)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestMediaPermsLauncher.launch(perms)
        }
    }

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
            binding.avatarView.setImageBitmap(scaled)
            binding.avatarView.visibility = View.VISIBLE
            binding.cameraIcon.visibility = View.GONE

            // Convert to Base64 (Anil Kr Mourya 2024)
            pickedImageBase64 = ImageUtils.encodeBitmapToBase64(scaled, quality = 70)
            selectedAvatarKey = null

            // Save Base64 to Firebase (Firebase 2019d)
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(uid)
                .child("avatar")
                .setValue(pickedImageBase64)
        } catch (e: Exception) {
            toast("Failed to load image: ${e.message}")
        }
    }

    private fun scaleBitmapIfNeeded(src: android.graphics.Bitmap, maxSide: Int): android.graphics.Bitmap {
        val w = src.width
        val h = src.height
        val largest = maxOf(w, h)
        if (largest <= maxSide) return src
        val scale = maxSide.toFloat() / largest
        val nw = (w * scale).toInt()
        val nh = (h * scale).toInt()
        return android.graphics.Bitmap.createScaledBitmap(src, nw, nh, true) // (GeeksforGeeks 2017)
    }

    // ----------------- Back handling -----------------
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (currentStep) {
            1 -> { toast("You cannot go back and change your date of birth"); return }
            2 -> { toast("Your current location has already been saved"); return }
        }

        if (currentStep > 0) {
            currentStep--
            showStep(currentStep, forward = false)
        } else {
            super.onBackPressed()
        }
    }

    private fun hideFriendsLoading() {
        binding.progressLoadingBars.visibility = View.GONE
        binding.tvLoadingBars.visibility = View.GONE
    }

    private fun showFriendsLoading() {
        binding.progressLoadingBars.visibility = View.VISIBLE
        binding.tvLoadingBars.visibility = View.VISIBLE
        binding.tvLoadingBars.text = "Loading friends..."
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
