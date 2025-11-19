package com.example.phuza.utils

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.adapters.AvatarAdapter
import com.example.phuza.databinding.BottomsheetChooseAvatarBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

object AvatarUtil {

    val avatarList = listOf(
        "no_avatar" to R.drawable.avatar_no_avatar,
        "avatar_1" to R.drawable.avatar_1,
        "avatar_2" to R.drawable.avatar_2,
        "avatar_3" to R.drawable.avatar_3,
        "avatar_4" to R.drawable.avatar_4,
        "avatar_5" to R.drawable.avatar_5,
        "avatar_6" to R.drawable.avatar_6,
        "avatar_7" to R.drawable.avatar_7,
        "avatar_8" to R.drawable.avatar_8,
        "avatar_9" to R.drawable.avatar_9
    )

    fun showAvatarSelector(context: Context, onAvatarSelected: (String) -> Unit) {
        val bottomSheetDialog = BottomSheetDialog(context)
        val bottomBinding = BottomsheetChooseAvatarBinding.inflate(
            android.view.LayoutInflater.from(context)
        )
        bottomSheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        bottomSheetDialog.window?.setWindowAnimations(R.style.BottomSheetAnimation)

        val spanCount = 3
        bottomBinding.avatarRecycler.layoutManager = GridLayoutManager(context, spanCount)

        // Add bottom-only spacing between rows
        val rowSpacing = 24 // px
        bottomBinding.avatarRecycler.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                val totalItems = parent.adapter?.itemCount ?: 0
                val rowCount = (totalItems + spanCount - 1) / spanCount
                val currentRow = position / spanCount + 1
                outRect.bottom = if (currentRow < rowCount) rowSpacing else 0
            }
        })

        var selectedAvatarKey: String? = null

        // Update your adapter to handle Pair<String, Int>
        val adapter = AvatarAdapter(avatarList) { avatarPair ->
            selectedAvatarKey = avatarPair.first
        }
        bottomBinding.avatarRecycler.adapter = adapter

        bottomBinding.btnChooseAvatar.setOnClickListener {
            if (selectedAvatarKey != null) {
                onAvatarSelected(selectedAvatarKey)
                bottomSheetDialog.dismiss()
            } else {
                Toast.makeText(context, "Please select an avatar", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheetDialog.setContentView(bottomBinding.root)
        bottomSheetDialog.show()
    }
}
