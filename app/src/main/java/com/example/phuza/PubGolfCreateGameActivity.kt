package com.example.phuza

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.example.phuza.api.DiscoverPubsResponse
import com.example.phuza.api.PubGolfGameViewModel
import com.example.phuza.api.RouteViewModel
import com.example.phuza.api.UiState
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.locationcomponent.location

// Screen: create a Pub Golf game from discovered pubs near you
class PubGolfCreateGameActivity : AppCompatActivity() {

    private lateinit var mapView: com.mapbox.maps.MapView
    private lateinit var fabCreateGame: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

    private val routeVm: RouteViewModel by viewModels()
    private val gameVm: PubGolfGameViewModel by viewModels()

    private var lastPoint: Point? = null
    private var centeredOnce = false
    private var lastDiscoverResponse: DiscoverPubsResponse? = null

    companion object {
        private const val SRC_PUBS = "pubgolf-pubs-source"
        private const val LAYER_PUBS = "pubgolf-pubs-layer"
        private const val IMG_PIN = "pubgolf-pin-image"
    }

    // Runtime permission launcher (Android Developer 2025)
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) enableLocation() else toast("Location permission is required")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pub_golf_create_game)

        mapView = findViewById(R.id.mapViewPubGolf)
        fabCreateGame = findViewById(R.id.fabCreateGame)

        findViewById<ImageButton>(R.id.btnBackPubGolf)
            .setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style ->
            ensurePinStyle(style)
            askForLocation()
        }

        fabCreateGame.setOnClickListener { onCreateGameClicked() }

        observePubs()
        observeGame()
    }

    // Add marker image, GeoJSON source, and symbol layer (Mapbox 2025)
    private fun ensurePinStyle(style: Style) {
        val pinDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_pub_pin)
            ?: error("Drawable ic_pub_pin not found in res/drawable")
        val pinBitmap: Bitmap = pinDrawable.toBitmap(config = Bitmap.Config.ARGB_8888)

        style.addImage(IMG_PIN, pinBitmap)

        if (style.getSource(SRC_PUBS) == null) {
            style.addSource(geoJsonSource(SRC_PUBS) { })
        }
        if (style.getLayer(LAYER_PUBS) == null) {
            style.addLayer(
                symbolLayer(LAYER_PUBS, SRC_PUBS) {
                    iconImage(IMG_PIN)
                    iconAnchor(IconAnchor.BOTTOM)
                    iconAllowOverlap(true)
                    iconIgnorePlacement(true)
                    iconSize(1.0)
                }
            )
        }
    }

    // Observe RouteViewModel pubs state (discover-pubs)
    private fun observePubs() {
        routeVm.pubsState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> {
                    fabCreateGame.isEnabled = false
                    fabCreateGame.text = "Finding pubs…"
                }
                is UiState.Error -> {
                    fabCreateGame.isEnabled = false
                    fabCreateGame.text = "Error"
                    toast(state.message)
                }
                is UiState.Success -> {
                    fabCreateGame.isEnabled = true
                    fabCreateGame.text = "Create Pub Golf Game"
                    lastDiscoverResponse = state.data

                    val style = mapView.getMapboxMap().getStyle() ?: return@observe

                    val features = state.data.pubs.map { pub ->
                        Feature.fromGeometry(
                            Point.fromLngLat(
                                pub.coordinates.longitude,
                                pub.coordinates.latitude
                            )
                        ).apply {
                            addStringProperty("name", pub.name)
                        }
                    }

                    style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(
                        SRC_PUBS
                    )?.featureCollection(FeatureCollection.fromFeatures(features))

                    val points = state.data.pubs.map {
                        Point.fromLngLat(it.coordinates.longitude, it.coordinates.latitude)
                    }
                    if (points.isNotEmpty()) {
                        val camera = mapView.getMapboxMap().cameraForCoordinates(
                            points,
                            EdgeInsets(100.0, 100.0, 100.0, 100.0),
                            null,
                            null
                        )
                        mapView.getMapboxMap().setCamera(camera)
                    }
                }
                else -> {}
            }
        }
    }

    // Observe game creation / start / score updates
    private fun observeGame() {
        gameVm.gameState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> {
                    fabCreateGame.isEnabled = false
                    fabCreateGame.text = "Creating game…"
                }
                is UiState.Error -> {
                    fabCreateGame.isEnabled = true
                    fabCreateGame.text = "Create Pub Golf Game"
                    toast(state.message)
                }
                is UiState.Success -> {
                    fabCreateGame.isEnabled = true
                    fabCreateGame.text = "Create Pub Golf Game"
                    val game = state.data

                    // remember this as the current game (for resume)
                    PubGolfPrefs.setLastGameId(this, game.id)

                    // Use server hostUid as "my" UID for the scorecard,
                    // so isHost = (game.hostUid == myUid) resolves to true.
                    val myCurrentUid = game.hostUid

                    val i = Intent(this, PubGolfScorecardActivity::class.java).apply {
                        putExtra(PubGolfScorecardActivity.EXTRA_GAME_ID, game.id)
                        putExtra(PubGolfScorecardActivity.EXTRA_MY_UID, myCurrentUid)
                    }
                    startActivity(i)
                    finish()
                }
                else -> {}
            }
        }
    }

    private fun onCreateGameClicked() {
        val resp = lastDiscoverResponse
        if (resp == null || resp.pubs.isEmpty()) {
            toast("No pubs found yet – still loading your location.")
            return
        }

        val title = "Pub Golf – ${resp.origin.name}"
        gameVm.createGameFromDiscover(resp, title)
    }

    // Request coarse/fine location permissions (Android Developer 2025)
    private fun askForLocation() {
        permLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Enable Mapbox location component (Mapbox 2025)
    private fun enableLocation() {
        val loc = mapView.location
        loc.updateSettings {
            enabled = true
            pulsingEnabled = true
            locationPuck = LocationPuck2D(
                topImage = ImageHolder.from(R.drawable.ic_puck_top),
                bearingImage = ImageHolder.from(R.drawable.ic_puck_bearing),
                shadowImage = ImageHolder.from(R.drawable.ic_puck_shadow)
            )
        }
        loc.addOnIndicatorPositionChangedListener { point ->
            lastPoint = point

            if (!centeredOnce) {
                centeredOnce = true
                mapView.getMapboxMap().flyTo(
                    CameraOptions.Builder()
                        .center(point)
                        .zoom(14.5)
                        .build()
                )
                // Auto-discover pubs near current location
                routeVm.discoverPubs(lat = point.latitude(), lon = point.longitude())
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // MapView lifecycle
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
}
