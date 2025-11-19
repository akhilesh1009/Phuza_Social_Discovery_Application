package com.example.phuza.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

class LocationUtil {
    // Request location permissions
    fun requestPermissions(
        activity: Activity,
        launcher: ActivityResultLauncher<Array<String>>,
        onGranted: () -> Unit
    ) {
        val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, // (Android Developer 2025)
                    Manifest.permission.ACCESS_COARSE_LOCATION // (Android Developer 2025)
                )
            )
        }
    }

    fun checkLocationSettings(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onReady: () -> Unit
    ) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000
        ).build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(activity)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            onReady()
        }.addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(e.resolution).build()
                    launcher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Toast.makeText(activity, "Failed to open location settings", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, "Location settings error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Fetch user location (uses fused provider)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun getAccurateLocation(
        activity: Activity,
        onLocation: (lat: Double, lon: Double) -> Unit,
        onError: (String) -> Unit
    ) {
        val fused = LocationServices.getFusedLocationProviderClient(activity)

        // Try last known first
        fused.lastLocation.addOnSuccessListener { last ->
            if (last != null) {
                onLocation(last.latitude, last.longitude)
            } else {
                // Request a fresh location
                val req = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(0)
                    .build()

                fused.getCurrentLocation(req, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            onLocation(loc.latitude, loc.longitude)
                        } else {
                            onError("Couldn't get current location. Try again.")
                        }
                    }
                    .addOnFailureListener { e ->
                        onError("Location error: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            onError("Location error: ${e.message}")
        }
    }
}

/*
 * REFERENCES
 *
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 */
