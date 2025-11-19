package com.example.phuza.managers

import android.content.Context // (Android Developers 2025)
import android.widget.Toast // (Android Developers 2025)

// (Android Developers 2025)
class OnboardingValidation {

    fun dateOfBirthValidation(day: String, month: String, year: String, context: Context): Boolean {
        return if (day.isEmpty() || month.isEmpty() || year.isEmpty()) {
            Toast.makeText(context, "Please select your full date of birth", Toast.LENGTH_SHORT).show()
            false
        } else true
    }

    fun selectBarsValidation(selectedBars: List<Any>, context: Context): Boolean {
        if (selectedBars.isEmpty()) {
            Toast.makeText(context, "Please select at least one bar", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    fun selectDrinksValidation(selectedDrink: Any?, context: Context): Boolean {
        if (selectedDrink == null) {
            Toast.makeText(context, "Please select a drink", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}

/*
 * REFERENCES
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 */
