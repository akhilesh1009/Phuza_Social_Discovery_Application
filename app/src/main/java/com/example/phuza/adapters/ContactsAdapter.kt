package com.example.phuza.adapters

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.phuza.R
import com.example.phuza.data.User
import com.example.phuza.utils.AvatarUtil
import java.util.Locale

class ContactsAdapter(
    private val onClick: (User) -> Unit
) : ListAdapter<User, ContactsAdapter.VH>(DIFF), Filterable {

    // Full, original list for filtering
    private val original = mutableListOf<User>()
    private var lastQuery: String = ""

    fun submitListSafe(items: List<User>) {
        original.clear()
        original.addAll(items)
        lastQuery = ""
        super.submitList(items.toList())
        Log.d(TAG, "submitListSafe: size=${items.size}")
    }

    fun filter(query: String) {
        lastQuery = query
        filter.filter(query)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_row, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        Log.d(TAG, "onBind @$position -> uid=${item.uid} firstName=${item.firstName} username=${item.username}")
        holder.bind(item)
    }

    class VH(v: View, private val onClick: (User) -> Unit) : RecyclerView.ViewHolder(v) {
        private val tvName: TextView = v.findViewById(R.id.tvName)
        private val tvMeta: TextView = v.findViewById(R.id.tvMeta)

        private val ivAvatar: ImageView = v.findViewById(R.id.ivAvatar)
        private var current: User? = null

        init {
            v.setOnClickListener { current?.let(onClick) }
            // If you also want the chat icon to trigger the click, you can:
            v.findViewById<View?>(R.id.ivStartChat)?.setOnClickListener {
                current?.let(onClick)
            }
        }

        fun bind(u: User) {
            current = u

            val displayName = buildDisplayName(u)
            tvName.text = displayName

            // Second line: username first, then email, then uid
            tvMeta.text = when {
                u.username.isNotBlank() -> "@${u.username}"
                u.email.isNotBlank()    -> u.email
                else                    -> u.uid
            }

            if (!u.avatar.isNullOrBlank()) {
                loadAvatar(u.avatar, ivAvatar)  // load via Glide or base64
            } else {
                ivAvatar.setImageResource(R.drawable.avatar_no_avatar)
            }
        }

//        fun loadAvatar(avatar: String?, imageView: ImageView) {
//            when {
//                avatar.isNullOrBlank() -> imageView.setImageResource(R.drawable.avatar_no_avatar)
//                avatar.startsWith("data:image") -> {
//                    // Base64 gallery-uploaded image
//                    val base64Data = avatar.split(",").getOrNull(1)
//                    base64Data?.let {
//                        val bytes = android.util.Base64.decode(it, android.util.Base64.DEFAULT)
//                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//                        imageView.setImageBitmap(bmp)
//                    } ?: imageView.setImageResource(R.drawable.avatar_no_avatar)
//                }
//                else -> {
//                    // Built-in avatar
//                    imageView.setImageResource(getAvatarResId(avatar))
//                }
//            }
//        }
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
            // Last fallback: maybe it's raw Base64 without data:image prefix
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



        fun getAvatarResId(avatarKey: String?): Int {
            if (avatarKey.isNullOrBlank() || avatarKey == "no_avatar") return R.drawable.avatar_no_avatar
            return AvatarUtil.avatarList.find { it.first == avatarKey }?.second ?: R.drawable.avatar_no_avatar
        }

        private fun buildDisplayName(u: User): String {
            return when {
                u.firstName.isNotBlank() && u.username.isNotBlank() ->
                    "${u.firstName} (${u.username})"
                u.firstName.isNotBlank() -> u.firstName
                u.username.isNotBlank()  -> u.username
                u.email.isNotBlank()     -> u.email
                else                     -> "User ${u.uid.take(6)}"
            }
        }
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val q = constraint?.toString()?.trim().orEmpty()
            val res = FilterResults()

            if (q.isEmpty()) {
                res.values = original.toList()
                res.count = original.size
            } else {
                val lower = q.lowercase(Locale.getDefault())
                val filtered = original.filter { u ->
                    val name = buildSearchableName(u).lowercase(Locale.getDefault())
                    val mail = u.email.lowercase(Locale.getDefault())
                    val user = u.username.lowercase(Locale.getDefault())
                    val uid  = u.uid.lowercase(Locale.getDefault())

                    name.contains(lower) ||
                            mail.contains(lower) ||
                            user.contains(lower) ||
                            uid.contains(lower)
                }
                res.values = filtered
                res.count = filtered.size
            }
            return res
        }

        private fun buildSearchableName(u: User): String {
            return listOf(u.firstName, u.username)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            val list = results?.values as? List<User> ?: emptyList()
            submitList(list.toList())
            Log.d(TAG, "publishResults: query='$lastQuery' size=${list.size}")
        }
    }

    companion object {
        private const val TAG = "ContactsAdapter"

        private val DIFF = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User): Boolean =
                oldItem.uid == newItem.uid

            override fun areContentsTheSame(oldItem: User, newItem: User): Boolean =
                oldItem == newItem
        }
    }
}
