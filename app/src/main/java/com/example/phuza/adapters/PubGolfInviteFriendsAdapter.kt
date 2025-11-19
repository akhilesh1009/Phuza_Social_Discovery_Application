package com.example.phuza

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.data.UserDto
import com.example.phuza.utils.AvatarUtil

class PubGolfInviteFriendsAdapter(
    private val onSelectionChanged: (selectedIds: List<String>) -> Unit
) : RecyclerView.Adapter<PubGolfInviteFriendsAdapter.FriendVH>() {

    private val items = mutableListOf<UserDto>()
    private val selected = mutableSetOf<String>()   // store selected user UIDs

    fun submitList(list: List<UserDto>) {
        items.clear()
        items.addAll(list)
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged(selected.toList())
    }

    fun getSelectedIds(): List<String> = selected.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendVH {
        val v = LayoutInflater.from(parent.context)
            // IMPORTANT: use the ROW layout, not the activity layout
            .inflate(R.layout.item_pub_golf_invite_friend, parent, false)
        return FriendVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: FriendVH, position: Int) {
        holder.bind(items[position], selected, onSelectionChanged)
    }

    class FriendVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvFriendName)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvFriendUsername)
        private val cbSelected: CheckBox = itemView.findViewById(R.id.cbSelected)

        fun bind(
            user: UserDto,
            selected: MutableSet<String>,
            onSelectionChanged: (List<String>) -> Unit
        ) {
            // Use uid as the stable key for selection
            val key = user.uid ?: return

            // Display name logic – tweak to match your UserDto fields
            tvName.text = user.firstName ?: user.username ?: key
            tvUsername.text = user.username?.let { "@$it" } ?: ""

            // Reset listener to avoid old callbacks firing on recycled views
            cbSelected.setOnCheckedChangeListener(null)

            // Set checkbox state based on current selection
            cbSelected.isChecked = selected.contains(key)

            val toggle = {
                if (selected.contains(key)) {
                    selected.remove(key)
                } else {
                    selected.add(key)
                }
                cbSelected.isChecked = selected.contains(key)
                onSelectionChanged(selected.toList())
            }

            itemView.setOnClickListener { toggle() }
            cbSelected.setOnClickListener { toggle() }

            loadAvatar(user.avatar, imgAvatar)
        }


            fun loadAvatar(avatar: String?, imageView: ImageView) {
                if (avatar.isNullOrBlank()) {
                    imageView.setImageResource(R.drawable.avatar_no_avatar)
                    return
                }

                when {
                    avatar.startsWith("data:image") -> {
                        // Base64 gallery-uploaded image
                        val base64Data = avatar.substringAfter(",")
                        try {
                            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            imageView.setImageBitmap(bmp)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            imageView.setImageResource(R.drawable.avatar_no_avatar)
                        }
                    }
                    AvatarUtil.avatarList.any { it.first == avatar } -> {
                        // Built-in avatar
                        imageView.setImageResource(
                            AvatarUtil.avatarList.find { it.first == avatar }?.second
                                ?: R.drawable.avatar_no_avatar
                        )
                    }
                    else -> {
                        // Last fallback: maybe raw Base64 without prefix
                        try {
                            val bytes = android.util.Base64.decode(avatar, android.util.Base64.DEFAULT)
                            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            imageView.setImageBitmap(bmp)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            imageView.setImageResource(R.drawable.avatar_no_avatar)
                        }
                    }
                }
            }

        }
    }

