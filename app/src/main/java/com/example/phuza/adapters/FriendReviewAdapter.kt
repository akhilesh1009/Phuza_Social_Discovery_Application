package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.Review
import com.example.phuza.utils.ImageUtils

// (Android Developers 2025)
class FriendReviewAdapter(
    private val items: MutableList<Review> = mutableListOf(),
    private val authorInfo: MutableMap<String, Pair<String?, String?>> = mutableMapOf()
) : RecyclerView.Adapter<FriendReviewAdapter.VH>() {

    // (GeeksforGeeks 2020)
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvReviewer: TextView = v.findViewById(R.id.tvReviewer)
        val ivProfile: ImageView = v.findViewById(R.id.ivProfile)
        val ivPlacePhoto: ImageView = v.findViewById(R.id.ivPlacePhoto)
        val tvBlurb: TextView = v.findViewById(R.id.tvBlurb)
        val ivHeart: ImageView = v.findViewById(R.id.ivHeart)
        val stars: List<ImageView> = listOf(
            v.findViewById(R.id.rStar1),
            v.findViewById(R.id.rStar2),
            v.findViewById(R.id.rStar3),
            v.findViewById(R.id.rStar4),
            v.findViewById(R.id.rStar5),
        )
    }

    // (Android Developers 2025)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        return VH(v)
    }

    // (GeeksforGeeks 2020)
    override fun getItemCount() = items.size

    // (GeeksforGeeks 2017)
    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]

        val (nameFromMap, avatarFromMap) = authorInfo[r.authorId.orEmpty()] ?: (null to null)
        val name = r.authorName ?: nameFromMap ?: "Someone"
        val avatar = r.authorAvatar ?: avatarFromMap

        h.tvReviewer.text = "$name’s review:"

        // Avatar handling (Anil Kr Mourya 2024; GeeksforGeeks 2017)
        if (avatar.isNullOrBlank()) {
            h.ivProfile.setImageResource(R.drawable.avatar_no_avatar)
        } else {
            val ctx = h.itemView.context
            val resId = ctx.resources.getIdentifier(avatar, "drawable", ctx.packageName)
            if (resId != 0) h.ivProfile.setImageResource(resId)
            else ImageUtils.decodeBase64ToBitmap(avatar)?.let { h.ivProfile.setImageBitmap(it) }
                ?: h.ivProfile.setImageResource(R.drawable.avatar_no_avatar)
        }

        // Place photo handling (Anil Kr Mourya 2024)
        val placeBmp = r.imageBase64?.let(ImageUtils::decodeBase64ToBitmap)
        if (placeBmp != null) {
            h.ivPlacePhoto.visibility = View.VISIBLE
            h.ivPlacePhoto.setImageBitmap(placeBmp)
        } else {
            h.ivPlacePhoto.visibility = View.GONE
        }

        // Description / blurb text (GeeksforGeeks 2020)
        if (!r.description.isNullOrBlank()) {
            h.tvBlurb.visibility = View.VISIBLE
            h.tvBlurb.text = r.description
            h.tvBlurb.setTextColor(ContextCompat.getColor(h.tvBlurb.context, R.color.milk))
        } else {
            h.tvBlurb.visibility = View.GONE
        }

        // Rating stars (GeeksforGeeks 2021)
        h.stars.forEachIndexed { i, iv ->
            val tint = if (i < r.rating) R.color.yellow else R.color.star_inactive
            iv.imageTintList = ContextCompat.getColorStateList(iv.context, tint)
        }

        // Heart icon (Android Knowledge 2023b)
        val heartIcon = if (r.liked) R.drawable.ic_heart else R.drawable.ic_heart_outline
        h.ivHeart.setImageResource(heartIcon)
        h.ivHeart.imageTintList = ContextCompat.getColorStateList(h.ivHeart.context, R.color.lavender)
    }

    // (Firebase 2019d)
    fun submit(newItems: List<Review>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    // (Ahamad 2018)
    fun putAuthor(uid: String, name: String?, avatar: String?) {
        authorInfo[uid] = name to avatar
        notifyDataSetChanged()
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
