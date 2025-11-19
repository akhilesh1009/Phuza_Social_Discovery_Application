package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.FriendshipStatus
import com.example.phuza.data.UserDto
import com.example.phuza.databinding.ItemUserBinding
import com.example.phuza.utils.AvatarUtil
import com.example.phuza.utils.ImageUtils

// (Android Developers 2025)
class UserAdapter(
    private val onPrimaryClick: (UserDto, FriendshipStatus?) -> Unit,
    private val onAccept: (UserDto) -> Unit,
    private val onReject: (UserDto) -> Unit
) : ListAdapter<UserDto, UserAdapter.VH>(DIFF) {

    private var statusByUserId: Map<String, FriendshipStatus> = emptyMap()
    private var incomingSet: Set<String> = emptySet()

    // (Firebase 2019d)
    fun submitUsers(list: List<UserDto>) = submitList(list)
    fun submitStatuses(map: Map<String, FriendshipStatus>) {
        statusByUserId = map
        notifyDataSetChanged()
    }
    fun submitIncoming(set: Set<String>) {
        incomingSet = set
        notifyDataSetChanged()
    }

    // (GeeksforGeeks 2020)
    object DIFF : DiffUtil.ItemCallback<UserDto>() {
        override fun areItemsTheSame(oldItem: UserDto, newItem: UserDto): Boolean {
            val oldKey = oldItem.uid ?: oldItem.id ?: oldItem.username
            val newKey = newItem.uid ?: newItem.id ?: newItem.username
            return oldKey == newKey
        }
        override fun areContentsTheSame(oldItem: UserDto, newItem: UserDto): Boolean = oldItem == newItem
    }

    // (GeeksforGeeks 2020)
    inner class VH(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {

        // (Anil Kr Mourya 2024; Android Developers 2025)
        fun bind(item: UserDto) = with(binding) {
            btnAdd.visibility = View.VISIBLE
            btnAdd.isEnabled = true
            incomingRow.visibility = View.GONE
            btnAccept.visibility = View.GONE
            btnReject.visibility = View.GONE
            btnAdd.setOnClickListener(null)
            btnAccept.setOnClickListener(null)
            btnReject.setOnClickListener(null)

            tvUsername.text = item.username?.let { "@$it" } ?: "@user"
            tvName.text = item.firstName ?: item.name ?: ""

            // Avatar binding (Anil Kr Mourya 2024)
            val avatarStr = item.avatar
            val mappedRes = avatarStr?.let { key -> AvatarUtil.avatarList.firstOrNull { it.first == key }?.second }
            when {
                mappedRes != null -> imgAvatar.setImageDrawable(ContextCompat.getDrawable(root.context, mappedRes))
                !avatarStr.isNullOrBlank() -> {
                    val base64 = avatarStr.substringAfter(",", avatarStr)
                    val bmp = ImageUtils.decodeBase64ToBitmap(base64)
                    if (bmp != null) imgAvatar.setImageBitmap(bmp) else imgAvatar.setImageResource(R.drawable.avatar_no_avatar)
                }
                else -> imgAvatar.setImageResource(R.drawable.avatar_no_avatar)
            }

            val key = item.uid ?: item.id ?: item.username.orEmpty()
            val status = statusByUserId[key]
            val incoming = incomingSet.contains(key)

            // Friendship logic handling (Ahamad 2018; Firebase 2019d)
            if (incoming && status == FriendshipStatus.requested) {
                btnAdd.visibility = View.GONE
                incomingRow.visibility = View.VISIBLE
                btnAccept.visibility = View.VISIBLE
                btnReject.visibility = View.VISIBLE

                btnAccept.contentDescription = root.context.getString(R.string.accept)
                btnReject.contentDescription = root.context.getString(R.string.reject)

                btnAccept.setOnClickListener { onAccept(item) }
                btnReject.setOnClickListener { onReject(item) }
            } else {
                when (status) {
                    null -> {
                        btnAdd.isEnabled = true
                        btnAdd.text = root.context.getString(R.string.add_friend)
                        btnAdd.backgroundTintList = ContextCompat.getColorStateList(root.context, R.color.lavender)
                    }
                    FriendshipStatus.requested -> {
                        btnAdd.isEnabled = true
                        btnAdd.text = root.context.getString(R.string.requested)
                        btnAdd.backgroundTintList = ContextCompat.getColorStateList(root.context, R.color.Dark_Purple)
                    }
                    FriendshipStatus.follow -> {
                        btnAdd.isEnabled = true
                        btnAdd.text = root.context.getString(R.string.following)
                    }
                    FriendshipStatus.following -> {
                        btnAdd.isEnabled = false
                        btnAdd.text = root.context.getString(R.string.they_follow)
                    }
                    FriendshipStatus.block -> {
                        btnAdd.isEnabled = false
                        btnAdd.text = root.context.getString(R.string.blocked)
                    }
                }

                btnAdd.setOnClickListener {
                    onPrimaryClick(item, status)

                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION && key.isNotBlank()) {
                        statusByUserId = when (status) {
                            null -> statusByUserId + (key to FriendshipStatus.requested)
                            FriendshipStatus.follow -> statusByUserId - key
                            else -> statusByUserId
                        }
                        notifyItemChanged(pos)
                    }
                }
            }
        }
    }

    // (Android Developers 2025)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    // (GeeksforGeeks 2020)
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
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
