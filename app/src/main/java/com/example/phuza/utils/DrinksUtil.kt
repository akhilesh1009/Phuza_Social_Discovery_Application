package com.example.phuza.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.adapters.DrinksAdapter
import com.example.phuza.data.Drink
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.database.FirebaseDatabase
object DrinksUtil {

    fun fetchDrinks(
        context: Context,
        chipGroup: ChipGroup,
        drinksAdapter: DrinksAdapter,
        onComplete: ((List<Drink>) -> Unit)? = null
    ) {
        val drinkList = mutableListOf<Drink>()
        val database = FirebaseDatabase.getInstance()
        val drinksRef = database.getReference("drinks")

        drinksRef.get().addOnSuccessListener { snapshot ->
            drinkList.clear()
            for (categorySnapshot in snapshot.children) {
                val category = categorySnapshot.key ?: continue
                for (drinkSnapshot in categorySnapshot.children) {
                    val drinkName = drinkSnapshot.getValue(String::class.java) ?: continue
                    drinkList.add(Drink(name = drinkName, category = category))
                }
            }

            // Update existing adapter
            drinksAdapter.updateData(drinkList)

            // Setup filter chips
            setupChips(context, chipGroup, drinksAdapter, drinkList)

            onComplete?.invoke(drinkList)
        }
    }
    private fun setupChips(
        context: Context,
        chipGroup: ChipGroup,
        drinksAdapter: DrinksAdapter,
        drinkList: List<Drink>
    ) {
        val categories = drinkList.map { it.category }.distinct()
        chipGroup.removeAllViews()

        for ((index,category) in categories.withIndex()) {
            // Inflate the chip from XML
            val chip = LayoutInflater.from(context).inflate(
                R.layout.item_chip,
                chipGroup,
                false
            ) as Chip

            chip.text = category

            chip.setOnClickListener {
                val filtered = drinkList.filter { it.category == category }
                drinksAdapter.updateData(filtered)
            }

            chipGroup.addView(chip)

            if (index == 0) {
                chip.isChecked = true   // visually select
                val filtered = drinkList.filter { it.category == category }
                drinksAdapter.updateData(filtered) // filter drinks
            }
        }
    }
}
