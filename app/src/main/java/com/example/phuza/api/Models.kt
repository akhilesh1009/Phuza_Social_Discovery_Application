package com.example.phuza.api

// ---- API envelope ----
data class ApiEnvelope<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val mode: String? = null
)

// ---- Basic types ----
data class Coordinates(
    val longitude: Double,
    val latitude: Double
)

data class StartingPoint(
    val name: String,
    val address: String,
    val coordinates: Coordinates
)
// TODO for Part 3
//// ---- Locations (save) ----
//data class LocationPayload( // POST /api/locations (save/upsert)
//    val name: String,
//    val address: String,
//    val coordinates: Coordinates,
//    val place_type: String? = null,
//    val properties: Map<String, Any?>? = null
//)

data class LocationDto(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val place_type: String? = null
)

//// TODO for Part 3
//// ---- Search (query mode on /api/locations) ----
//data class SearchRequest(
//    val query: String,
//    val lon: Double? = null,
//    val lat: Double? = null,
//    val limit: Int? = 8,
//    val country: String = "ZA",
//    val language: String = "en"
//)

data class GeometryDto(val coordinates: List<Double>) {
    fun lon() = coordinates.getOrNull(0)
    fun lat() = coordinates.getOrNull(1)
}

data class MapboxFeatureDto(
    val id: String?,
    val text: String,
    val place_name: String,
    val geometry: GeometryDto,
    val coordinates: Coordinates,
    val place_type: String?,
    val properties: Map<String, Any?>? = null
)

//// ---- Discover Route ----
//data class DiscoverRouteRequest(
//    val startingPoint: StartingPoint,
//    val numberOfPubs: Int? = null,      // let server default to 10
//    val radiusMeters: Int? = null       // let server default to 10000
//)

// ---- Route Response ----
data class RouteNode(
    val name: String,
    val address: String,
    val coordinates: Coordinates
)

data class RouteStep(
    val order: Int,
    val name: String,
    val address: String,
    val coordinates: Coordinates,
    val distanceFromPrevious: Double
)

data class RouteResponse(
    val route: List<RouteNode>,
    val totalDistance: Double,      // km
    val estimatedTime: Int,         // minutes
    val routeDetails: List<RouteStep>,
    val discoveredPubs: Int? = null,
    val searchRadius: Int? = null
)

data class DiscoverPubsRequest(
    val origin: StartingPoint,
    val numberOfPubs: Int? = null,
    val radiusMeters: Int? = null
)

data class DiscoveredPub(
    val name: String,
    val address: String,
    val coordinates: Coordinates,
    val distanceFromPrevious: Double
)

data class DiscoverPubsMeta(
    val discoveredPubs: Int,
    val orderedCount: Int,
    val totalDistanceKm: Double,
    val searchRadius: Int
)

data class DiscoverPubsResponse(
    val origin: StartingPoint,
    val pubs: List<DiscoveredPub>,
    val meta: DiscoverPubsMeta
)

/*
 * REFERENCES
 *
 * Mapbox. 2025. “Mapbox Docs”.
 * https://docs.mapbox.com/#search
 * [accessed 16 September 2025].
 */
