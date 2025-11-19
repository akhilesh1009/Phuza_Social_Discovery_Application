package com.example.phuza.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import com.example.phuza.R
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

// Utility object for handling Date of Birth (DOB) dropdown
object DateOfBirthUtil {

    // MONTHS OF THE YEAR
    val Months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    // Days from 1 to 31 (we disable invalid ones depending on month/year)
    private val Days = (1..31).map { it.toString() }


    //Generate a list of years based on a minimum year and minimum age.
    fun getYears(minYear: Int = 1984, minAge: Int = 18): List<String> {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val maxYear = currentYear - minAge
        return (minYear..maxYear).map { it.toString() }
    }

    fun setupDropdown(
        context: Context,
        dropdown: AutoCompleteTextView,
        items: List<String>
    ) {
        val adapter = ArrayAdapter(context, R.layout.dropdown_item, items)
        dropdown.setAdapter(adapter)
    }

    //Set up the Day dropdown with logic to disable invalid days based on the selected month and year.
    fun setupDayDropdown(
        context: Context,
        dayDropdown: AutoCompleteTextView,
        monthDropdown: AutoCompleteTextView,
        yearDropdown: AutoCompleteTextView
    ) {
        val adapter = object : ArrayAdapter<String>(context, R.layout.dropdown_item, Days) {

            // Disable days that exceed the max day for the selected month/year
            override fun isEnabled(position: Int): Boolean {
                val monthIndex = getMonthIndex(monthDropdown.text.toString())
                val year = yearDropdown.text.toString().toIntOrNull() ?: 2000
                val maxDay = getMaxDays(monthIndex, year)

                return getItem(position)?.toIntOrNull()?.let { it <= maxDay } ?: true
            }

            // Gray out invalid days visually
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                if (!isEnabled(position)) {
                    view.setTextColor(context.getColor(android.R.color.darker_gray))
                } else {
                    view.setTextColor(context.getColor(android.R.color.black))
                }
                return view
            }
        }
        dayDropdown.setAdapter(adapter)
    }

    // Rebuild the Day dropdown whenever Month/Year changes.
    fun updateDays(
        context: Context,
        dayDropdown: AutoCompleteTextView,
        monthDropdown: AutoCompleteTextView,
        yearDropdown: AutoCompleteTextView
    ) {
        setupDayDropdown(context, dayDropdown, monthDropdown, yearDropdown)

        val previousDay = dayDropdown.text.toString().toIntOrNull()
        val monthIndex = getMonthIndex(monthDropdown.text.toString())
        val year = yearDropdown.text.toString().toIntOrNull() ?: 2000
        val maxDay = getMaxDays(monthIndex, year)

        if (previousDay != null && previousDay <= maxDay) {
            dayDropdown.setText(previousDay.toString(), false)
        } else {
            dayDropdown.setText("", false)
        }
    }

    // Leap year check
    fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    // Max days in a given month/year
    fun getMaxDays(monthIndex: Int, year: Int): Int = when (monthIndex) {
        0, 2, 4, 6, 7, 9, 11 -> 31
        3, 5, 8, 10 -> 30
        1 -> if (isLeapYear(year)) 29 else 28
        else -> 31
    }

    // Convert month name → index (0 = Jan, 11 = Dec)
    fun getMonthIndex(monthName: String): Int = when (monthName) {
        "January" -> 0; "February" -> 1; "March" -> 2; "April" -> 3
        "May" -> 4; "June" -> 5; "July" -> 6; "August" -> 7
        "September" -> 8; "October" -> 9; "November" -> 10; "December" -> 11
        else -> -1
    }

    // Set up dropdown arrow icons (up when expanded, down when closed).
    @SuppressLint("UseCompatLoadingForDrawables")
    fun setupDropdownArrow(
        context: Context,
        dropdown: AutoCompleteTextView,
        layout: TextInputLayout
    ) {
        dropdown.setOnClickListener {
            layout.endIconDrawable = context.getDrawable(R.drawable.ic_up_arrow)
            dropdown.showDropDown()
        }
        dropdown.setOnDismissListener {
            layout.endIconDrawable = context.getDrawable(R.drawable.ic_down_arrow)
        }
    }

    fun saveDob(context: Context, day: String, month: String, year: String) {
        val dob = "$day $month $year"
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("users")
            .child(userId)
            .child("dateofbirth")
            .setValue(dob)
    }

}