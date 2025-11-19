package com.example.phuza.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.FriendsFavBar
import com.example.phuza.utils.ImageUtils

// (Android Developers 2025)
class FriendsFavBarAdapter(
    private val bars: List<FriendsFavBar>
) : RecyclerView.Adapter<FriendsFavBarAdapter.VH>() {

    // (GeeksforGeeks 2020)
    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.name)
        private val avatar1: ImageView = view.findViewById(R.id.avatar1)
        private val avatar2: ImageView = view.findViewById(R.id.avatar2)
        private val avatar3: ImageView = view.findViewById(R.id.avatar3)
        private val avatarMore: TextView = view.findViewById(R.id.avatar_more)

        // (GeeksforGeeks 2020)
        fun bind(item: FriendsFavBar) {
            name.text = item.barName

            val targets = listOf(avatar1, avatar2, avatar3)
            val avatarsToShow = item.friendAvatars.take(3)

            // Display avatars (Anil Kr Mourya 2024; GeeksforGeeks 2017)
            targets.forEachIndexed { i, img ->
                if (i < avatarsToShow.size) {
                    setAvatar(img, avatarsToShow[i])
                    img.visibility = View.VISIBLE
                } else {
                    img.visibility = View.GONE
                }
            }

            // Handle extra avatars (GeeksforGeeks 2020)
            val extra = item.friendAvatars.size - 2
            if (extra > 0) {
                avatarMore.visibility = View.VISIBLE
                avatarMore.text = "+$extra"
            } else {
                avatarMore.visibility = View.GONE
            }
        }

        // (Anil Kr Mourya 2024)
        private fun setAvatar(view: ImageView, value: String) {
            val bmp: Bitmap? = if (looksLikeBase64Image(value)) {
                ImageUtils.decodeBase64ToBitmap(value)
            } else {
                val resId = view.context.resources.getIdentifier(
                    value, "drawable", view.context.packageName
                )
                if (resId != 0) BitmapFactory.decodeResource(view.context.resources, resId)
                else null
            }

            if (bmp != null) {
                view.setImageBitmap(getRoundedBitmap(bmp))
            } else {
                view.setImageResource(R.drawable.avatar_no_avatar)
            }
        }

        /** Crop a square bitmap into a circle (GeeksforGeeks 2017) */
        private fun getRoundedBitmap(src: Bitmap): Bitmap {
            val size = Math.min(src.width, src.height)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, size, size)
            val rectF = RectF(rect)
            canvas.drawOval(rectF, paint) // draw circular mask
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

            val left = (src.width - size) / 2
            val top = (src.height - size) / 2
            canvas.drawBitmap(src, -left.toFloat(), -top.toFloat(), paint)
            return output
        }
    }

    // (Android Developers 2025)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friends_bars, parent, false)
        return VH(v)
    }

    // (GeeksforGeeks 2020)
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(bars[position])
    }

    // (Android Developers 2025)
    override fun getItemCount() = bars.size

    // (Anil Kr Mourya 2024)
    private fun looksLikeBase64Image(value: String): Boolean {
        if (value.startsWith("data:image")) return true
        if (value.length < 100) return false
        return value.all { it.isLetterOrDigit() || it in "+/=\n\r" }
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
