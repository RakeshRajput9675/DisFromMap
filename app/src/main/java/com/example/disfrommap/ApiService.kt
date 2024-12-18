package com.example.disfrommap

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

// Geocoding response
data class GeocodeResult(
    val lat: Double,
    val lon: Double
)

// Route response
data class RouteResponse(
    val features: List<Feature>
)

data class Feature(
    val geometry: Geometry,
    val properties: Properties
)

data class Geometry(
    val coordinates: List<List<Double>>
)

data class Properties(
    val distance: Double // distance in meters
)

interface ApiService {

    // Geocoding API to get coordinates of a place
    @GET("search?format=json")
    fun getCoordinates(@Query("q") place: String): Call<List<GeocodeResult>>

    // OpenRouteService API to get a route between two coordinates
    @GET("v2/directions/driving-car")
    fun getRoute(
        @Query("api_key") apiKey: String,
        @Query("start") start: String,
        @Query("end") end: String
    ): Call<RouteResponse>
}
