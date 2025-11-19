package com.example.phuza.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.AppNotification
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlin.math.abs

// (Android Developers 2025)
class NotificationsAdapter(
    private val onClick: (AppNotification) -> Unit = {}
) : ListAdapter<AppNotification, NotificationsAdapter.VH>(DIFF) {

    // (GeeksforGeeks 2020)
    object DIFF : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(a: AppNotification, b: AppNotification) = a.id == b.id
        override fun areContentsTheSame(a: AppNotification, b: AppNotification) = a == b
    }

    // Firebase Realtime Database reference (Firebase 2019d)
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val avatarCache = HashMap<String, String?>()

    // (GeeksforGeeks 2020)
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val img: ImageView = v.findViewById(R.id.imgAvatar)
        private val title: TextView = v.findViewById(R.id.txtTitle)
        private val sub: TextView = v.findViewById(R.id.txtSub)
        private val time: TextView = v.findViewById(R.id.txtTime)

        // (Ahamad 2018; Firebase 2019d)
        fun bind(n: AppNotification) {
            // Placeholder while loading
            img.setImageResource(R.drawable.avatar_no_avatar)
            loadAvatar(n.fromUid, img)

            val name = n.fromName ?: "Someone"
            val username = n.fromUsername?.takeIf { it.isNotBlank() } ?: ""
            val who = if (username.isNotEmpty()) "$name" else name

            val verb = when (n.type) {
                "follow_request" -> "added you!"
                "follow_accept"  -> "accepted your follow request"
                "checkin"        -> n.message
                else             -> n.message.ifBlank { "sent a notification" }
            }

            title.text = when (n.type) {
                "checkin" -> verb
                else      -> "$who $verb"
            }

            sub.text = "@${username}".takeIf { username.isNotEmpty() } ?: n.fromUid

            // Calculate relative time (Android Developer 2025)
            val millis = n.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            val now = System.currentTimeMillis()
            val diff = abs(now - millis)

            time.text = if (diff < DateUtils.MINUTE_IN_MILLIS) {
                "now"
            } else {
                DateUtils.getRelativeTimeSpanString(
                    millis,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            }

            itemView.alpha = if (n.read) 0.65f else 1f

            itemView.setOnClickListener { onClick(n) }
        }
    }

    // (Android Developers 2025)
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_notification, p, false))

    // (GeeksforGeeks 2020)
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    // Resetting views (GeeksforGeeks 2017)
    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.itemView.findViewById<ImageView>(R.id.imgAvatar)
            .setImageResource(R.drawable.avatar_no_avatar)
        holder.itemView.findViewById<ImageView>(R.id.imgAvatar).tag = null
    }

    // Load avatar from Firebase (Firebase 2019d; Anil Kr Mourya 2024)
    private fun loadAvatar(uid: String, into: ImageView) {
        into.tag = uid

        val cached = avatarCache[uid]
        if (cached != null) {
            applyAvatarName(into, cached)
            return
        }

        usersRef.child(uid).child("avatar")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val avatarName = snapshot.getValue(String::class.java) // e.g. "avatar_5"
                    avatarCache[uid] = avatarName
                    if (into.tag == uid) applyAvatarName(into, avatarName)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Ignore and keep placeholder
                }
            })
    }

    // (GeeksforGeeks 2017)
    private fun applyAvatarName(view: ImageView, avatarName: String?) {
        if (!avatarName.isNullOrBlank() && !avatarName.startsWith("http", ignoreCase = true)) {
            val resId = view.resources.getIdentifier(
                avatarName, "drawable", view.context.packageName
            )
            if (resId != 0) {
                view.setImageResource(resId)
                return
            }
        }

        view.setImageResource(R.drawable.avatar_no_avatar)
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
