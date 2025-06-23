package com.example.onenighttent

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.size
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.onenighttent.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

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


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val campgrounds = mutableListOf<CampgroundFeature>()

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val TAG = "MapsActivity"
        private const val GEOJSON_URL = "https://1nitetent.com/app/themes/1nitetent/assets/json/campgrounds.geojson"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMarkerClickListener(this)
        enableMyLocation()
        loadCampgroundData()
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val campgroundFeature = marker.tag as? CampgroundFeature
        if (campgroundFeature != null) {
            val title = campgroundFeature.properties.name
            val description = campgroundFeature.properties.description.let {
                Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()
            }
            val link = campgroundFeature.properties.link

            val bottomSheet =
                MarkerInfoBottomSheetFragment.newInstance(title, description, link)
            bottomSheet.show(supportFragmentManager, "MarkerInfoBottomSheet")
        } else {
            Log.e(
                TAG,
                "Marker tag is null or not the expected type for ${marker.title}"
            )
            val basicBottomSheet = MarkerInfoBottomSheetFragment.newInstance(
                marker.title ?: "Unknown",
                marker.snippet,
                null
            )
            basicBottomSheet.show(supportFragmentManager, "MarkerInfoBottomSheet_Basic")
        }
        return true
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (::mMap.isInitialized) {
                mMap.isMyLocationEnabled = true
                zoomToUserLocation()
            } else {
                Log.w(TAG, "Map not ready in enableMyLocation")
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun zoomToUserLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    if (::mMap.isInitialized) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 10f))
                    }
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get last location", e)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission denied. Cannot show current location.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadCampgroundData() {
        activityScope.launch {
            Log.d(TAG, "Starting to load campground data from URL...")
            try {
                val data: CampgroundData? = withContext(Dispatchers.IO) {
                    var urlConnection: HttpURLConnection? = null
                    try {
                        val url = URL(GEOJSON_URL)
                        urlConnection = url.openConnection() as HttpURLConnection
                        urlConnection.requestMethod = "GET"
                        urlConnection.connectTimeout = 15000 // 15 seconds
                        urlConnection.readTimeout = 10000 // 10 seconds

                        val responseCode = urlConnection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val inputStream = urlConnection.inputStream
                            val reader = InputStreamReader(inputStream)
                            Gson().fromJson(reader, CampgroundData::class.java)
                        } else {
                            Log.e(TAG, "HTTP error code: $responseCode from $GEOJSON_URL")
                            null // Indicate failure
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during network request or parsing", e)
                        null // Indicate failure
                    } finally {
                        urlConnection?.disconnect()
                    }
                }

                if (data != null) {
                    Log.d(TAG, "Successfully loaded and parsed ${data.features.size} campgrounds.")
                    campgrounds.clear()
                    campgrounds.addAll(data.features)
                    addMarkersToMap()
                } else {
                    Log.e(TAG, "Failed to load or parse campground data from URL.")
                    Toast.makeText(this@MapsActivity, "Could not load campground data.", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) { // Catch any other exceptions from the coroutine itself
                Log.e(TAG, "Exception in loadCampgroundData coroutine", e)
                Toast.makeText(this@MapsActivity, "Error loading campground data.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addMarkersToMap() {
        if (!::mMap.isInitialized) {
            Log.e(TAG, "mMap not initialized before adding markers.")
            return
        }
        mMap.clear()

        campgrounds.forEach { campground ->
            if (campground.geometry.coordinates.size >= 2) {
                val position = LatLng(
                    campground.geometry.coordinates[1], // Latitude
                    campground.geometry.coordinates[0]  // Longitude
                )
                val marker = mMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(campground.properties.name)
                )
                marker?.tag = campground
            } else {
                Log.w(TAG, "Campground '${campground.properties.name}' has invalid coordinates.")
            }
        }
        Log.d(TAG, "Added ${campgrounds.size} markers to the map.")
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}