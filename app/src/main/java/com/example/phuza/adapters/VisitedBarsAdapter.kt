package com.example.phuza.adapters

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.Review

// Displays a visited bars list with dates and star ratings (Android Developers 2025)
class VisitedBarsAdapter : ListAdapter<Review, VisitedBarsAdapter.VH>(DIFF) {

    // Efficient list updates via DiffUtil (Android Developers 2025)
    object DIFF : DiffUtil.ItemCallback<Review>(){
        override fun areItemsTheSame(oldItem: Review, newItem: Review) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Review, newItem: Review) = oldItem == newItem
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view){
        private val tvBarName: TextView = view.findViewById(R.id.tvBarName)
        private val tvDate: TextView = view.findViewById(R.id.tvDate)
        private val llStarsContainer: LinearLayout = view.findViewById(R.id.llStarsContainer)

        private val ivHeart: ImageView = view.findViewById(R.id.ivHeart)


        fun bind(item: Review){
            tvBarName.text = item.place

            // Format timestamp to readable date (Android Developers 2025)
            val date = DateFormat.format("dd MMMM yyyy", item.timestamp).toString()
            tvDate.text = date

            // Build star row programmatically (Android Developers 2025)
            llStarsContainer.removeAllViews()
            val starColor = ContextCompat.getColorStateList(itemView.context, R.color.yellow)
            val inactiveColor = ContextCompat.getColorStateList(itemView.context, R.color.star_inactive)


            for (i in 1..5){
                val star = ImageView(itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        width = 40
                        height = 40
                        if (i > 1) marginStart = 8
                    }
                    setImageResource(R.drawable.ic_star)
                    imageTintList = if (i <= item.rating) starColor else inactiveColor
                }
                llStarsContainer.addView(star)
            }

            // Set heart based on whether the user liked the bar
            if(item.liked) {   // assuming your Review has a `liked: Boolean`
                ivHeart.setImageResource(R.drawable.ic_heart)
            } else {
                ivHeart.setImageResource(R.drawable.ic_heart_outline)
            }

        }
    }

    // Standard RecyclerView adapter pattern (Android Developers 2025)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_visited_bar, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}

/*
 * REFERENCES
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Android Developers. 2025. “Text Styling and Formatting (DateFormat)”.
 * https://developer.android.com/reference/android/text/format/DateFormat
 * [accessed 30 September 2025].
 */
