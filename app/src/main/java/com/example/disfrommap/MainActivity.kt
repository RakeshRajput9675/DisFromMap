package com.example.disfrommap

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.disfrommap.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Retrofit Services
    private val geocodeService by lazy { createRetrofitService("https://nominatim.openstreetmap.org/") }
    private val routeService by lazy { createRetrofitService("https://api.openrouteservice.org/") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize OSMDroid
        Configuration.getInstance().load(
            this,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        )

        // Configure MapView
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }

        // Set up button click listener
        binding.btnShowRoute.setOnClickListener {
            val sourceName = binding.etSourcePlace.text.toString().trim()
            val destName = binding.etDestPlace.text.toString().trim()

            if (sourceName.isBlank() || destName.isBlank()) {
                showToast("Please enter both source and destination")
            } else {
                fetchCoordinatesAndRoute(sourceName, destName)
            }
        }
    }

    private fun fetchCoordinatesAndRoute(sourceName: String, destName: String) {
        // Clear previous overlays
        binding.mapView.overlays.clear()

        // Fetch source and destination coordinates concurrently
        val sourceCoordinatesCall = geocodeService.getCoordinates(sourceName)
        val destCoordinatesCall = geocodeService.getCoordinates(destName)

        sourceCoordinatesCall.enqueue(object : Callback<List<GeocodeResult>> {
            override fun onResponse(call: Call<List<GeocodeResult>>, response: Response<List<GeocodeResult>>) {
                val sourceCoord = response.body()?.firstOrNull()
                if (sourceCoord == null) {
                    showToast("Could not find source coordinates")
                    return
                }

                destCoordinatesCall.enqueue(object : Callback<List<GeocodeResult>> {
                    override fun onResponse(call: Call<List<GeocodeResult>>, response: Response<List<GeocodeResult>>) {
                        val destCoord = response.body()?.firstOrNull()
                        if (destCoord == null) {
                            showToast("Could not find destination coordinates")
                            return
                        }

                        // Calculate distances
                        val haversineDistance = calculateHaversineDistance(
                            sourceCoord.lat, sourceCoord.lon,
                            destCoord.lat, destCoord.lon
                        )

                        // Request and display route
                        requestRouteAndDisplay(sourceCoord, destCoord, haversineDistance)
                    }

                    override fun onFailure(call: Call<List<GeocodeResult>>, t: Throwable) {
                        showToast("Failed to fetch destination coordinates: ${t.message}")
                    }
                })
            }

            override fun onFailure(call: Call<List<GeocodeResult>>, t: Throwable) {
                showToast("Failed to fetch source coordinates: ${t.message}")
            }
        })
    }

    private fun requestRouteAndDisplay(
        source: GeocodeResult,
        destination: GeocodeResult,
        haversineDistance: Double
    ) {
        val start = "${source.lon},${source.lat}"
        val end = "${destination.lon},${destination.lat}"

        routeService.getRoute(
            apiKey = "5b3ce3597851110001cf624865683dbe57e74bb49302ed93a81c50dd",
            start = start,
            end = end
        ).enqueue(object : Callback<RouteResponse> {
            override fun onResponse(call: Call<RouteResponse>, response: Response<RouteResponse>) {
                val routeFeature = response.body()?.features?.firstOrNull()
                if (routeFeature != null) {
                    displayRouteOnMap(routeFeature.geometry.coordinates)

                    // Display distance on the UI
                    val distanceInKm = routeFeature.properties.distance / 1000.0
                    val distanceText = "Distance: ${String.format("%.2f", haversineDistance)} km"

                    binding.tvDistance.text = distanceText
                } else {
                    showToast("Failed to calculate the route distance")
                }
            }

            override fun onFailure(call: Call<RouteResponse>, t: Throwable) {
                showToast("Failed to fetch route: ${t.message}")
            }
        })
    }

    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun displayRouteOnMap(routeCoordinates: List<List<Double>>) {
        val routeLine = Polyline().apply {
            color = android.graphics.Color.RED
            width = 10f
        }
        routeCoordinates.forEach { coord -> routeLine.addPoint(GeoPoint(coord[1], coord[0])) }
        binding.mapView.overlays.add(routeLine)
        binding.mapView.controller.apply {
            setZoom(10.0)
            setCenter(GeoPoint(routeCoordinates.first()[1], routeCoordinates.first()[0]))
        }
        binding.mapView.invalidate()
    }

    private fun createRetrofitService(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
