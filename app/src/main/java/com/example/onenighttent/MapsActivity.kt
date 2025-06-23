package com.example.onenighttent

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Initialize ViewModel using the by viewModels() KTX delegate
    private val mapViewModel: MapViewModel by viewModels()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val TAG = "MapsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        observeViewModel()
    }

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMarkerClickListener(this)
        enableMyLocation()
    }

    private fun observeViewModel() {
        mapViewModel.campgrounds.observe(this) { campgroundsList ->
            Log.d(TAG, "Campgrounds LiveData updated with ${campgroundsList.size} items.")
            addMarkersToMap(campgroundsList)
        }

        mapViewModel.isLoading.observe(this) { isLoading ->
            // Optionally: Show/hide a progress bar
            if (isLoading) {
                Log.d(TAG, "Loading campground data...")
                // binding.progressBar.visibility = View.VISIBLE // Example
            } else {
                Log.d(TAG, "Finished loading campground data.")
                // binding.progressBar.visibility = View.GONE // Example
            }
        }

        mapViewModel.errorMessages.observe(this) { errorMessage ->
            errorMessage?.let {
                Log.e(TAG, "Error: $it")
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                mapViewModel.clearErrorMessage() // Clear error after showing it
            }
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val campgroundFeature = marker.tag as? CampgroundFeature
        if (campgroundFeature != null) {
            val title = campgroundFeature.properties.name
            // Consider moving HTML parsing to ViewModel if it's heavy, or do it here
            val description = campgroundFeature.properties.description.let {
                               Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()
            } // Pass raw HTML or plain text
            val link = campgroundFeature.properties.link

            val bottomSheet =
                MarkerInfoBottomSheetFragment.newInstance(title, description, link)
            bottomSheet.show(supportFragmentManager, "MarkerInfoBottomSheet")
        } else {
            Log.e(TAG, "Marker tag is null or not the expected type for ${marker.title}")
            val basicBottomSheet = MarkerInfoBottomSheetFragment.newInstance(
                marker.title ?: "Unknown",
                marker.snippet,
                null
            )
            basicBottomSheet.show(supportFragmentManager, "MarkerInfoBottomSheet_Basic")
        }
        return true
    }
    private fun addMarkersToMap(campgroundsList: List<CampgroundFeature>) {
        if (!::mMap.isInitialized) {
            Log.e(TAG, "mMap not initialized before adding markers.")
            return
        }
        mMap.clear() // Clear existing markers

        campgroundsList.forEach { campground ->
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
        Log.d(TAG, "Added ${campgroundsList.size} markers to the map.")
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

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}