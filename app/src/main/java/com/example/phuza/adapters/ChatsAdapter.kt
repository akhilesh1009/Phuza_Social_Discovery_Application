package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.phuza.R
import com.example.phuza.utils.ImageUtils
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatPreview(
    val chatId: String,
    val peerUid: String,
    val displayName: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val avatar: String? = null
)

class ChatsAdapter(
    private val onChatClicked: (ChatPreview) -> Unit
) : ListAdapter<ChatPreview, ChatsAdapter.ChatVH>(Diff) {

    init {
        setHasStableIds(true)
    }

    object Diff : DiffUtil.ItemCallback<ChatPreview>() {
        override fun areItemsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean =
            oldItem.chatId == newItem.chatId

        override fun areContentsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean =
            oldItem == newItem
    }

    override fun getItemId(position: Int): Long =
        getItem(position).chatId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_overview, parent, false)
        return ChatVH(view, onChatClicked)
    }

    override fun onBindViewHolder(holder: ChatVH, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatVH(
        itemView: View,
        private val onChatClicked: (ChatPreview) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val imgAvatar: ShapeableImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(item: ChatPreview) {
            tvUsername.text = item.displayName
            tvName.text = item.displayName

            tvLastMessage.text = item.lastMessage
            tvTime.text = formatTime(item.lastTimestamp)

            bindAvatar(item.avatar)

            itemView.setOnClickListener { onChatClicked(item) }
        }

        private fun bindAvatar(avatarValue: String?) {
            val ctx = itemView.context
            imgAvatar.scaleType = ImageView.ScaleType.CENTER_CROP

            if (avatarValue.isNullOrBlank()) {
                imgAvatar.setImageResource(R.drawable.avatar_no_avatar)
                return
            }

            // Heuristic: base64 vs local drawable name vs URL
            if (looksLikeBase64Image(avatarValue)) {
                val bmp = ImageUtils.decodeBase64ToBitmap(avatarValue)
                if (bmp != null) {
                    imgAvatar.setImageBitmap(bmp)
                } else {
                    imgAvatar.setImageResource(R.drawable.avatar_no_avatar)
                }
                return
            }

            // Try as local drawable name
            val resId = ctx.resources.getIdentifier(avatarValue, "drawable", ctx.packageName)
            if (resId != 0) {
                imgAvatar.setImageResource(resId)
                return
            }

            // Fallback: treat as URL and load with Glide
            Glide.with(ctx)
                .load(avatarValue)
                .placeholder(R.drawable.avatar_no_avatar)
                .error(R.drawable.avatar_no_avatar)
                .into(imgAvatar)
        }

        private fun looksLikeBase64Image(value: String): Boolean {
            if (value.startsWith("data:image")) return true
            if (value.length < 100) return false
            return value.all { it.isLetterOrDigit() || it in "+/=\n\r" }
        }

        private fun formatTime(epochMillis: Long): String {
            if (epochMillis <= 0L) return ""

            val diff = System.currentTimeMillis() - epochMillis
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            return when {
                seconds < 60 -> "Just now"
                minutes < 60 -> "${minutes}m"
                hours < 24 -> "${hours}h"
                days < 7 -> "${days}d"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
            }
        }
    }
}
