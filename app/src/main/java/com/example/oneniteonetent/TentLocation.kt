package com.example.oneniteonetent

data class TentLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String?,
    val imageUrl: String?,
    val amenities: List<String>?,
    // Add other relevant fields like availability, booking link, etc.
)