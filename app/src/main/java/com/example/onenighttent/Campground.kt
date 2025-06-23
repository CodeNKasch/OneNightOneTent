package com.example.onenighttent



// Your data classes (ensure these match your GeoJSON structure)
data class CampgroundProperties(
    val name: String,
    val description: String,
    val link: String?, // Assuming link can be null
    val `marker-color`: String?, // Added based on GeoJSON snippet
    val `marker-symbol`: String?  // Added based on GeoJSON snippet
)

data class Geometry(val type: String, val coordinates: List<Double>) // Expects [longitude, latitude]
data class CampgroundFeature(val properties: CampgroundProperties, val geometry: Geometry)
data class CampgroundData(val features: List<CampgroundFeature>)

