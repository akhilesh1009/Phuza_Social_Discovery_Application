package com.example.phuza

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.phuza.R
import com.example.phuza.data.BarUi
import com.example.phuza.data.UserDto
import com.example.phuza.databinding.ActivityProfileBinding
import com.example.phuza.databinding.ItemBarBinding
import com.example.phuza.utils.AvatarUtil
import com.example.phuza.utils.ImageUtils
import com.example.phuza.data.ProfileViewModel
import kotlin.collections.forEachIndexed
import kotlin.getValue
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.example.phuza.SettingsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

// (Android Developers 2025)
class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ProfileViewModel by viewModels() // (Android Developers 2025)

    // (Android Developers 2025)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // initialise view binding
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets(R.id.main) // (Android Developers 2025)
        setupBottomNav(binding.bottomNavBar.root.findViewById(R.id.bottomNav), R.id.nav_profile) // (Android Developers 2025)

        observeUserProfile()   // (Android Developers 2025)
        observeFavoriteBars()  // (Android Developers 2025)
        setupListItemTitles()  // (Android Developers 2025)
        setupClickListeners()  // (Ahamad 2018)
    }

    // (Ahamad 2018)
    private fun setupClickListeners(){
        binding.settingsIcon.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            // start new activity
            startActivity(intent)
        }
        binding.itemBarsVisited.root.setOnClickListener {
            startActivity(Intent(this, VisitedBarsActivity::class.java))
        }

        binding.itemFriends.root.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }
    }

    // Observe user profile from ViewModel (Android Developers 2025)
    private fun observeUserProfile(){
        viewModel.userProfile.observe(this){ user ->
            user?.let {
                binding.profileName.text = it.name ?: it.firstName ?: it.username?: "user"

                val avatarStr = it.avatar

                val mappedRes = avatarStr?.let { key ->
                    AvatarUtil.avatarList.firstOrNull{ it.first == key }?.second
                }
                when {
                    mappedRes != null -> {
                        binding.avatar.setImageResource(mappedRes)
                    }
                    !avatarStr.isNullOrBlank() -> {
                        val base64 = avatarStr.substringAfter(",", avatarStr)
                        val bmp = ImageUtils.decodeBase64ToBitmap(base64) // (Anil Kr Mourya 2024)

                        if(bmp != null){
                            binding.avatar.setImageBitmap(bmp)
                        }
                        else{
                            Glide.with(this) // (GeeksforGeeks 2017)
                                .load(avatarStr)
                                .placeholder(R.drawable.avatar_placeholder)
                                .error(R.drawable.avatar_no_avatar)
                                .into(binding.avatar)
                        }
                    }
                    else -> binding.avatar.setImageResource(R.drawable.avatar_no_avatar)
                }

                binding.profileLocation.text = it.location ?: this.getString(R.string.set_your_location)
                binding.myDop.text = it.favoriteDrink ?: this.getString(R.string.select_your_dop)

            }
        }
    }

    // Observe and render favorite bars (Android Developers 2025)
    private fun observeFavoriteBars(){
        viewModel.favoriteBars.observe(this){ bars ->
            val barIncludes = listOf(
                binding.bar1Include,
                binding.bar2Include,
                binding.bar3Include
            )

            barIncludes.forEachIndexed { index, includeBinding ->
                if(index < bars.size){
                    val bar = bars[index]
                    includeBinding.name.text = bar.name   // directly through binding
                    includeBinding.root.visibility = View.VISIBLE
                } else {
                    includeBinding.root.visibility = View.GONE
                }
            }
        }
    }

    // (Android Developers 2025)
    private fun setupListItemTitles() {
        setProfileTitle(
            binding.itemBarsVisited.root,
            getString(R.string.bars_visited)
        )
        setProfileTitle(
            binding.itemFriends.root,
            getString(R.string.friends)
        )
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


    // (Android Developers 2025)
    private fun setProfileTitle(itemView: android.view.View, title:String){
        itemView.findViewById<TextView>(R.id.list_item_title)?.text = title
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
