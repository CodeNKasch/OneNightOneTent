package com.example.oneniteonetent // Your package name

import java.util.Date
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.oneniteonetent.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.text.toLongOrNull

// Data class to represent a campground feature from your GeoJSON
data class CampgroundFeature(
    val properties: CampgroundProperties,
    val geometry: CampgroundGeometry
)

data class CampgroundProperties(
    val id: String, // This will be a generated ID or parsed from name
    val name: String,
    val image_url: String? = null,
    val description: String?,
    val link: String?
)

data class CampgroundGeometry(
    val type: String,
    val coordinates: List<Double> // [longitude, latitude]
)


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    // ... (your existing properties: mMap, binding, fusedLocationClient, requestPermissionLauncher) ...

    private val geoJsonUrl = "https://1nitetent.com/app/themes/1nitetent/assets/json/campgrounds.geojson"
    private val cacheFileName = "campgrounds_cache.json"
    private val cacheDurationMillis = 24 * 60 * 60 * 1000 // 24 hours

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient // For getting location

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MapsActivity", "Location permission granted")
                enableMyLocation()
            } else {
                Log.d("MapsActivity", "Location permission denied")
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) // Initialize FusedLocationProviderClient

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setInfoWindowAdapter(CustomInfoWindowAdapter()) // Set your custom adapter
        mMap.setOnMarkerClickListener { marker ->
            val campground = marker.tag as? CampgroundFeature
            if (campground != null) {
                // Option A: Show default info window (if you haven't set a custom one or want it to still appear)
                // marker.showInfoWindow() // Usually happens by default, but you can explicitly call it.

                // Option B: Do something else, e.g., show a Toast with more details
                Toast.makeText(
                    this,
                    "Tapped on: ${campground.properties.name}\nLink: ${campground.properties.link}",
                    Toast.LENGTH_LONG
                ).show()

                // Option C: Launch a BottomSheet or a new Activity
                // Example:
                // val intent = Intent(this, CampgroundDetailActivity::class.java)
                // intent.putExtra("CAMPGROUND_ID", campground.properties.id) // Pass some identifier
                // startActivity(intent)

                // If you handle the click completely and don't want the default behavior
                // (which is to center the map on the marker and show the info window), return true.
                // If you return false, the default behavior will also occur.
                return@setOnMarkerClickListener false // Let default behavior (show info window) also happen
            }
            return@setOnMarkerClickListener false // For markers without valid tags or if you want default behavior
        }
        Log.d("MapsActivity", "onMapReady: Map is ready!")

        // Check for location permission when map is ready
        checkLocationPermissionAndProceed()

        // Load campground data
        loadCampgroundData()
    }

    private fun loadCampgroundData() {
        Toast.makeText(this, "loading ...", Toast.LENGTH_SHORT).show()

        GlobalScope.launch(Dispatchers.IO) { // Perform network and file operations off the main thread
            val cachedData = getCachedCampgroundData()
            if (cachedData != null) {
                Log.d("MapsActivity", "Using cached campground data.")
                val campgrounds = parseGeoJson(cachedData)
                withContext(Dispatchers.Main) {
                    addCampgroundMarkers(campgrounds)
                }
            } else {
                Log.d("MapsActivity", "Fetching fresh campground data.")
                try {
                    val jsonData = fetchGeoJson(URL(geoJsonUrl))
                    if (jsonData != null) {
                        saveCampgroundDataToCache(jsonData)
                        val campgrounds = parseGeoJson(jsonData)
                        withContext(Dispatchers.Main) {
                            addCampgroundMarkers(campgrounds)
                        }
                    } else {
                        Log.e("MapsActivity", "Failed to fetch GeoJSON data.")
                        // Optionally, try to load from an older cache if primary fetch fails
                        // and you want to show stale data as a last resort.
                    }
                } catch (e: Exception) {
                    Log.e("MapsActivity", "Error fetching or parsing GeoJSON", e)
                    // Consider loading from an older cache here too if applicable
                }
            }
        }
    }

    private fun getCachedCampgroundData(): String? {
        val cacheFile = File(filesDir, cacheFileName)
        val metadataFile = File(filesDir, "$cacheFileName.metadata")

        if (!cacheFile.exists() || !metadataFile.exists()) {
            Log.d("MapsActivity", "Cache file or metadata not found.")
            return null
        }

        try {
            val lastFetchTime = metadataFile.readText().toLongOrNull()
            if (lastFetchTime == null || (Date().time - lastFetchTime) > cacheDurationMillis) {
                Log.d("MapsActivity", "Cache expired or invalid metadata.")
                cacheFile.delete() // Delete outdated cache
                metadataFile.delete()
                return null
            }
            Log.d("MapsActivity", "Cache is fresh. Reading from cache file.")
            return BufferedReader(FileReader(cacheFile)).use { it.readText() }
        } catch (e: Exception) {
            Log.e("MapsActivity", "Error reading from cache", e)
            return null
        }
    }

    private fun saveCampgroundDataToCache(jsonData: String) {
        try {
            FileWriter(File(filesDir, cacheFileName)).use { it.write(jsonData) }
            FileWriter(File(filesDir, "$cacheFileName.metadata")).use { it.write(Date().time.toString()) }
            Log.d("MapsActivity", "Campground data saved to cache.")
        } catch (e: Exception) {
            Log.e("MapsActivity", "Error saving data to cache", e)
        }
    }

    private suspend fun fetchGeoJson(url: URL): String? {
        return withContext(Dispatchers.IO) { // Ensure this runs on a background thread
            var connection: HttpURLConnection? = null
            try {
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 10000  // 10 seconds

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.use { it.readText() }
                    Log.d("MapsActivity", "Successfully fetched GeoJSON.")
                    response
                } else {
                    Log.e("MapsActivity", "HTTP error code: ${connection.responseCode} for URL: $url")
                    null
                }
            } catch (e: Exception) {
                Log.e("MapsActivity", "Exception during fetchGeoJson for URL: $url", e)
                null
            } finally {
                connection?.disconnect()
            }
        }
    }


    private fun parseGeoJson(jsonData: String): List<CampgroundFeature> {
        val features = mutableListOf<CampgroundFeature>()
        try {
            val geoJsonObject = JSONObject(jsonData)
            val featuresArray = geoJsonObject.getJSONArray("features")

            for (i in 0 until featuresArray.length()) {
                val featureObject = featuresArray.getJSONObject(i)
                val propertiesObject = featureObject.getJSONObject("properties") // This is correct
                val geometryObject = featureObject.getJSONObject("geometry")
                val coordinatesArray = geometryObject.getJSONArray("coordinates")

                // Create a unique ID for each feature since it's not in the properties
                val generatedId = UUID.randomUUID().toString()

                val properties = CampgroundProperties(
                    id = generatedId, // Use the generated ID
                    name = propertiesObject.getString("name"),
                    description = propertiesObject.optString("description", null), // Get description
                    link = propertiesObject.optString("link", null),             // Get link
                    image_url = propertiesObject.optString("image_url", null)    // image_url might not be present in all
                )

                val coordinates = listOf(
                    coordinatesArray.getDouble(0), // Longitude
                    coordinatesArray.getDouble(1)  // Latitude
                )

                val geometry = CampgroundGeometry(
                    type = geometryObject.getString("type"),
                    coordinates = coordinates
                )
                features.add(CampgroundFeature(properties, geometry))
            }
            Log.d("MapsActivity", "Successfully parsed ${features.size} campgrounds.")
        } catch (e: Exception) {
            Log.e("MapsActivity", "Error parsing GeoJSON", e)
            // Handle parsing error, maybe return empty list or throw custom exception
        }
        return features
    }

    private fun addCampgroundMarkers(campgrounds: List<CampgroundFeature>) {
        if (!::mMap.isInitialized) {
            Log.e("MapsActivity", "Map not initialized when trying to add markers.")
            return
        }
        for (campground in campgrounds) {
            if (campground.geometry.type == "Point") {
                val position = LatLng(campground.geometry.coordinates[1], campground.geometry.coordinates[0])
                mMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(campground.properties.name)
                        // Add a snippet - e.g., the first few words of the description
                        .snippet(campground.properties.description?.take(80) + "...") // Show first 80 chars
                )?.tag = campground // Store the full campground data in the marker's tag
            }
        }
        Log.d("MapsActivity", "Added ${campgrounds.size} campground markers to the map.")
    }

    private fun checkLocationPermissionAndProceed() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
                Log.d("MapsActivity", "ACCESS_FINE_LOCATION permission already granted.")
                enableMyLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.
                Log.d("MapsActivity", "Showing rationale for ACCESS_FINE_LOCATION.")
                // For simplicity, directly requesting again. In a real app, show a dialog.
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                Toast.makeText(this, "Location permission is needed to show your current location.", Toast.LENGTH_LONG).show()

            }
            else -> {
                // Directly ask for the permission.
                Log.d("MapsActivity", "Requesting ACCESS_FINE_LOCATION permission.")
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission") // Suppress warning as permission is checked by enableMyLocation
    private fun enableMyLocation() {
        // Double check permission just in case, though it should be granted if this is called
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            mMap.isMyLocationEnabled = true // Enable the My Location layer (blue dot)
            Log.d("MapsActivity", "My Location layer enabled.")

            // Get last known location and move camera
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)) // Zoom level 15
                        Log.d("MapsActivity", "Moved camera to last known location: $currentLatLng")
                        // You could also add a marker here if desired, but the blue dot serves this purpose
                        // mMap.addMarker(MarkerOptions().position(currentLatLng).title("My Current Location"))
                    } else {
                        Log.d("MapsActivity", "Last known location is null. May need to request updates or device location is off.")
                        Toast.makeText(this, "Could not get current location. Make sure location is enabled on your device.", Toast.LENGTH_LONG).show()
                        // Fallback: If no last location, maybe zoom to a default area or do nothing further.
                        // val defaultLocation = LatLng(37.4220, -122.0841) // Googleplex
                        // mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MapsActivity", "Error trying to get last GPS location", e)
                    Toast.makeText(this, "Error getting location: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Log.d("MapsActivity", "enableMyLocation called but permission not granted (should not happen if logic is correct).")
            // This case should ideally not be reached if checkLocationPermissionAndProceed is called first
            // You might want to re-request permission or guide the user.
        }
    }

    // Inside MapsActivity class
    inner class CustomInfoWindowAdapter : GoogleMap.InfoWindowAdapter {

        private val window: View = layoutInflater.inflate(R.layout.custom_info_window, null)

        override fun getInfoWindow(marker: Marker): View? {
            // This method is called first. If it returns a view, that view is used.
            // If it returns null, then getInfoContents() is called.
            // We'll populate the view in getInfoContents for simplicity here.
            renderWindowText(marker, window)
            return window
        }

        override fun getInfoContents(marker: Marker): View? {
            // This method is called if getInfoWindow() returns null.
            // It's used to update the contents of the default info window frame.
            // We are using a fully custom window, so this could also return null
            // if all rendering is done in getInfoWindow.
            // However, to be safe and demonstrate, let's populate here too.
            // If getInfoWindow returns a view, this method is typically not called.
            renderWindowText(marker, window) // Or inflate and populate a different view for contents only
            return null // Return null if you want to use the default info window frame with custom contents
            // For a fully custom window, the logic in getInfoWindow should be primary.
        }

        private fun renderWindowText(marker: Marker, view: View) {
            val titleTextView = view.findViewById<TextView>(R.id.info_window_title)
            val descriptionTextView = view.findViewById<TextView>(R.id.info_window_description)
            val linkTextView = view.findViewById<TextView>(R.id.info_window_link)

            val campground = marker.tag as? CampgroundFeature // Retrieve the data from the tag

            if (campground != null) {
                titleTextView.text = campground.properties.name

                // --- Display HTML description ---
                val htmlDescription = campground.properties.description
                if (!htmlDescription.isNullOrEmpty()) {
                    descriptionTextView.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Html.fromHtml(htmlDescription, Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        @Suppress("DEPRECATION")
                        Html.fromHtml(htmlDescription)
                    }
                } else {
                    descriptionTextView.text = "No description available."
                }
                // --- End HTML description ---

                if (!campground.properties.link.isNullOrEmpty()) {
                    linkTextView.text = "Contact: ${campground.properties.link}"
                    linkTextView.visibility = View.VISIBLE
                } else {
                    linkTextView.visibility = View.GONE
                }
            } else {
                titleTextView.text = marker.title ?: "Unknown Location"
                descriptionTextView.text = marker.snippet ?: ""
                linkTextView.visibility = View.GONE
            }
        }
    }
}