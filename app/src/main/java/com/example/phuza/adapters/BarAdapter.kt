package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.BarUi
//(GeeksforGeeks 2020)
class BarAdapter(
    private val userLat: Double,
    private val userLon: Double,
    private val onSelectionChanged: (List<BarUi>) -> Unit = {}
) : ListAdapter<BarUi, BarAdapter.VH>(DIFF) {
    private val selectedKeys = LinkedHashSet<String>()


    private val knownByKey = HashMap<String, BarUi>()

    private fun keyOf(item: BarUi) = "${item.name}|${item.latitude},${item.longitude}"

    fun getSelectedBars(): List<BarUi> = selectedKeys.mapNotNull { knownByKey[it] }
    //(GeeksforGeeks 2020)
    override fun submitList(list: List<BarUi>?) {
        list?.forEach { knownByKey[keyOf(it)] = it }
        super.submitList(list)
        onSelectionChanged(getSelectedBars())
    }
    //(GeeksforGeeks 2020)
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BarUi>() {
            override fun areItemsTheSame(oldItem: BarUi, newItem: BarUi): Boolean =
                oldItem.name == newItem.name &&
                        oldItem.latitude == newItem.latitude &&
                        oldItem.longitude == newItem.longitude

            override fun areContentsTheSame(oldItem: BarUi, newItem: BarUi): Boolean = oldItem == newItem
        }
    }
    //(GeeksforGeeks 2020)
    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val imgSelector: ImageView = view.findViewById(R.id.imgSelector)

        fun bind(item: BarUi) {
            tvName.text = item.name

            val selected = selectedKeys.contains(keyOf(item))
            imgSelector.setBackgroundResource(
                if (selected) R.drawable.selector_circle_filled
                else R.drawable.selector_circle_bg
            )

            itemView.setOnClickListener {
                val key = keyOf(item)
                if (selectedKeys.contains(key)) {
                    selectedKeys.remove(key)
                    notifyItemChanged(bindingAdapterPosition)
                    itemView.rootView.findViewById<TextView>(R.id.tvMaxSelectionWarning)
                        ?.visibility = View.GONE
                } else {
                    if (selectedKeys.size >= 3) {
                        itemView.rootView.findViewById<TextView>(R.id.tvMaxSelectionWarning)
                            ?.visibility = View.VISIBLE
                    } else {
                        selectedKeys.add(key)
                        // record in known map in case this item disappears due to filtering
                        knownByKey[key] = item
                        notifyItemChanged(bindingAdapterPosition)
                        itemView.rootView.findViewById<TextView>(R.id.tvMaxSelectionWarning)
                            ?.visibility = View.GONE
                    }
                }
                // Fire callback with full selection (not just visible)
                onSelectionChanged(getSelectedBars())
            }
        }
    }
    //(GeeksforGeeks 2020)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bar, parent, false)
        return VH(v)
    }

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

