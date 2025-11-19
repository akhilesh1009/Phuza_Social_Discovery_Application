package com.example.phuza.managers

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.example.phuza.R
import com.example.phuza.utils.DateOfBirthUtil
import com.google.android.material.textfield.TextInputLayout

class DateOfBirthManager (
    private val context: Context,
    private val monthDropdown: AutoCompleteTextView,
    private val dayDropdown: AutoCompleteTextView,
    private val yearDropdown: AutoCompleteTextView,
    private val monthLayout: TextInputLayout,
    private val dayLayout: TextInputLayout,
    private val yearLayout: TextInputLayout

) {
    fun setup() {
        // Setup months
        setDropdown(monthDropdown, DateOfBirthUtil.Months)

        // Setup years (18+ by default)
        val years = DateOfBirthUtil.getYears()
        setDropdown(yearDropdown, years)

        // Setup days initially
        updateDays()

        // Recalculate days whenever month/year changes
        monthDropdown.setOnItemClickListener { _, _, _, _ -> updateDays() }
        yearDropdown.setOnItemClickListener { _, _, _, _ -> updateDays() }

        // Setup dropdown arrows
        setupDropdownArrow(monthDropdown, monthLayout)
        setupDropdownArrow(dayDropdown, dayLayout)
        setupDropdownArrow(yearDropdown, yearLayout)
    }

    private fun setDropdown(dropdown: AutoCompleteTextView, items: List<String>) {
        val adapter = ArrayAdapter(context, R.layout.dropdown_item, items)
        dropdown.setAdapter(adapter)
    }

    private fun updateDays() {
        val monthIndex = DateOfBirthUtil.getMonthIndex(monthDropdown.text.toString())
        val year = yearDropdown.text.toString().toIntOrNull() ?: 2000
        val maxDay = DateOfBirthUtil.getMaxDays(monthIndex, year)

        val days = (1..maxDay).map { it.toString() }
        setDropdown(dayDropdown, days)

        val currentDay = dayDropdown.text.toString().toIntOrNull()
        if (currentDay == null || currentDay > maxDay) {
            dayDropdown.setText("", false)
        }
    }

    private fun setupDropdownArrow(
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
}
