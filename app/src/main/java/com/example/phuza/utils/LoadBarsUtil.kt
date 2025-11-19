package com.example.phuza.utils

import android.location.Location
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.phuza.api.RouteViewModel
import com.example.phuza.api.UiState
import com.example.phuza.adapters.BarAdapter
import com.example.phuza.data.BarUi
import kotlin.math.roundToInt
import java.util.Locale

object LoadBarsUtil {

    fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(aLat, aLon, bLat, bLon, results)
        return results[0]
    }

    fun formatDistance(meters: Float): String =
        if (meters >= 950f) {
            val km = meters / 1000f
            val text = if (km < 10f) String.format(Locale.getDefault(), "%.1f", km)
            else (km.roundToInt()).toString()
            "$text km"
        } else "${meters.roundToInt()} m"


    fun startLoadingBars(
        activity: FragmentActivity,
        adapter: BarAdapter,
        userLat: Double,
        userLon: Double,
        progressBar: View,
        loadingText: View,
        query: String? = null
    ) {
        val vm = ViewModelProvider(activity)[RouteViewModel::class.java]
        vm.pubsState.removeObservers(activity)

        fun List<BarUi>.sortedByDistance(): List<BarUi> =
            this.sortedBy { bar -> distanceMeters(userLat, userLon, bar.latitude, bar.longitude) }

        fun render(list: List<BarUi>) {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            adapter.submitList(list.sortedByDistance())
        }

        fun showNoResults() {
            progressBar.visibility = View.GONE
            (loadingText as? TextView)?.text = "No results match your search"
            loadingText.visibility = View.VISIBLE
            adapter.submitList(emptyList())
        }

        // 1) If we’re filtering (query not blank): render from whatever we already have, no loader.
        val q = query?.trim().orEmpty()
        if (q.isNotEmpty()) {
            val state = vm.pubsState.value
            if (state is UiState.Success) {
                val allBars = state.data.pubs.map {
                    BarUi(it.name, it.address, it.coordinates.latitude, it.coordinates.longitude)
                }
                val qLower = q.lowercase(Locale.getDefault())
                val filtered = allBars.filter { bar ->
                    (bar.name?.lowercase(Locale.getDefault())?.contains(qLower) == true) ||
                            (bar.address?.lowercase(Locale.getDefault())?.contains(qLower) == true)
                }
                if (filtered.isEmpty()) showNoResults() else render(filtered)
                return
            }
        }

        // 2) Observe and render
        vm.pubsState.observe(activity) { state ->
            when (state) {
                is UiState.Success -> {
                    val allBars = state.data.pubs.map {
                        BarUi(it.name, it.address, it.coordinates.latitude, it.coordinates.longitude)
                    }
                    if (q.isEmpty()) {

                        render(allBars)
                    } else {

                        val qLower = q.lowercase(Locale.getDefault())
                        val filtered = allBars.filter { bar ->
                            (bar.name?.lowercase(Locale.getDefault())?.contains(qLower) == true) ||
                                    (bar.address?.lowercase(Locale.getDefault())?.contains(qLower) == true)
                        }
                        if (filtered.isEmpty()) showNoResults() else render(filtered)
                    }
                }
                is UiState.Error -> {
                    progressBar.visibility = View.GONE
                    loadingText.visibility = View.GONE
                    Toast.makeText(activity, "Failed to load bars", Toast.LENGTH_SHORT).show()
                }
                is UiState.Loading -> {
                    // Only show loader if we *don’t* have data yet and query is empty
                    if (q.isEmpty() && vm.pubsState.value !is UiState.Success) {
                        progressBar.visibility = View.VISIBLE
                        (loadingText as? TextView)?.text = "Finding Bars..."
                        loadingText.visibility = View.VISIBLE
                    } else {
                        progressBar.visibility = View.GONE
                        loadingText.visibility = View.GONE
                    }
                }
                UiState.Idle -> {
                    // Show nothing
                    progressBar.visibility = View.GONE
                    loadingText.visibility = View.GONE
                }
            }
        }

        // 3) If query is empty and we don’t have data yet, actually fetch once.
        val current = vm.pubsState.value
        if (q.isEmpty()) {
            if (current !is UiState.Success) {
                progressBar.visibility = View.VISIBLE
                (loadingText as? TextView)?.text = "Finding Bars..."
                loadingText.visibility = View.VISIBLE
                vm.discoverPubs(userLat, userLon)
            } else {
                // Already have data: render immediately (no loader, no API)
                val allBars = current.data.pubs.map {
                    BarUi(it.name, it.address, it.coordinates.latitude, it.coordinates.longitude)
                }
                render(allBars)
            }
        } else {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
        }
    }
}

/*
 * REFERENCES
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 */
