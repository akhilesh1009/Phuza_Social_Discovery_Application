package com.example.phuza.api

import androidx.lifecycle.LiveData // (Android Developers 2025)
import androidx.lifecycle.MutableLiveData // (Android Developers 2025)
import androidx.lifecycle.ViewModel // (Android Developers 2025)
import androidx.lifecycle.viewModelScope // (Android Developers 2025)
import com.example.phuza.api.RetrofitInstance.api // (GeeksforGeeks 2023)
import kotlinx.coroutines.launch // (Android Developers 2025)


class RouteViewModel : ViewModel() { // (Android Developers 2025)
    //TODO Part 3
//    private val _routeState = MutableLiveData<UiState<RouteResponse>>(UiState.Idle)
//
//    private val _searchState = MutableLiveData<UiState<List<MapboxFeatureDto>>>(UiState.Idle)
//    private val _saveState = MutableLiveData<UiState<LocationDto>>(UiState.Idle)

    private val _pubsState = MutableLiveData<UiState<DiscoverPubsResponse>>() // (Android Developers 2025)
    val pubsState: LiveData<UiState<DiscoverPubsResponse>> = _pubsState // (Android Developers 2025)

    fun discoverPubs(lat: Double, lon: Double) {
        viewModelScope.launch { // (Android Developers 2025)
            _pubsState.value = UiState.Loading
            try {
                val body = DiscoverPubsRequest(
                    origin = StartingPoint(
                        name = "Current location",
                        address = "Your position",
                        coordinates = Coordinates(
                            longitude = lon,
                            latitude = lat
                        )
                    ),
                    numberOfPubs = 20, //Number of pubs
                    radiusMeters = 10000 //Radius in meters
                )

                val response = api.discoverPubs(body) // (GeeksforGeeks 2023)
                val envelope = response.body()

                if (response.isSuccessful && envelope?.data != null) {
                    _pubsState.value = UiState.Success(envelope.data)
                } else {
                    _pubsState.value = UiState.Error(envelope?.message ?: "Error: ${response.code()}")
                }
            } catch (e: Exception) {
                _pubsState.value = UiState.Error(e.message ?: "Unknown error")
            }
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
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”.
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 */
