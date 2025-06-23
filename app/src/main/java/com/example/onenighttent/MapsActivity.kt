package com.example.onenighttent

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import java.util.Date
import java.util.UUID


// Data classes for GeoJSON
data class CampgroundFeature(
    val properties: CampgroundProperties,
    val geometry: CampgroundGeometry
)

data class CampgroundProperties(
    val id: String, // This is now a generated ID
    val name: String,
    val description: String?,
    val link: String?,
    val image_url: String? = null
)

data class CampgroundGeometry(
    val type: String,
    val coordinates: List<Double> // [longitude, latitude]
)



class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val geoJsonUrl = "https://1nitetent.com/app/themes/1nitetent/assets/json/campgrounds.geojson"
    private val cacheFileName = "campgrounds_cache.json"
    private val cacheDurationMillis = 24 * 60 * 60 * 1000 // 24 hours

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MapsActivity", "Location permission granted")
                enableMyLocation()
            } else {
                Log.d("MapsActivity", "Location permission denied")
                Toast.makeText(
                    this,
                    "Location permission denied. Cannot show current location.",
                    Toast.LENGTH_LONG
                ).show()
                // Consider guiding user to settings if permission is permanently denied
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Log.d("MapsActivity", "onMapReady: Map is ready!")

        // Enable UI settings
        mMap.uiSettings.isZoomControlsEnabled = true

        mMap.setOnMarkerClickListener(this)
        // mMap.setInfoWindowAdapter(CustomInfoWindowAdapter())

        checkLocationPermissionAndProceed()
        loadCampgroundData()
    }


    override fun onMarkerClick(marker: Marker): Boolean {
        // Retrieve data associated with the marker.
        // You could have stored your CampgroundProperties object (or just relevant fields) in the marker's tag.
        val campgroundData = marker.tag as? CampgroundFeature // Example: if you stored CampgroundFeature in tag
        // Or if you only stored specific data in the tag, retrieve it.
        // If you didn't use the tag, you might need to find the data based on marker.id or marker.title

        val title = marker.title ?: "Details"
        var description: String? = null
        var link: String? = null

        if (campgroundData != null) {
            // Assuming CampgroundFeature has a 'properties' field of type CampgroundProperties
            description = campgroundData.properties.description?.let {
                Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()
            }
            link = campgroundData.properties.link // Links are typically not HTML, but if they are, handle similarly
        } else {
            // Fallback if tag data is not available or not in the expected format
            // You might fetch data based on marker.id from a local list/database
            Log.w("MapsActivity", "Campground data not found in marker tag for: ${marker.title}")
            // For now, we can use the snippet as a fallback if you have it
            // Assuming snippet might also contain HTML
            description = marker.snippet?.let {
                Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()
            }
        }


        val bottomSheet = MarkerInfoBottomSheetFragment.newInstance(title, description, link)
        bottomSheet.show(supportFragmentManager, "MarkerInfoBottomSheet")

        // Return true to indicate that we have consumed the event and don't want the default behavior
        // (which is to center the map on the marker and open the info window).
        return true
    }


    private fun checkLocationPermissionAndProceed() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("MapsActivity", "ACCESS_FINE_LOCATION permission already granted.")
                enableMyLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Log.d("MapsActivity", "Showing rationale for ACCESS_FINE_LOCATION.")
                // In a real app, show a dialog explaining why the permission is needed
                Toast.makeText(
                    this,
                    "Location permission is needed to show your current location.",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                Log.d("MapsActivity", "Requesting ACCESS_FINE_LOCATION permission.")
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission") // Suppressed because permission is checked before calling
    private fun enableMyLocation() {
        if (!::mMap.isInitialized) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 13f))
                    Log.d("MapsActivity", "Moved camera to current location: $currentLatLng")
                } ?: run {
                    Log.d("MapsActivity", "Last known location is null.")
                    // Optionally, move to a default location if current location is unavailable
                }
            }
        } else {
            Log.d("MapsActivity", "Location permission not granted, cannot enable my location.")
        }
    }

    private fun loadCampgroundData() {
        GlobalScope.launch(Dispatchers.IO) {
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
                    }
                } catch (e: Exception) {
                    Log.e("MapsActivity", "Error fetching or parsing GeoJSON", e)
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
                cacheFile.delete()
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
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000 // 15 seconds
                connection.readTimeout = 15000  // 15 seconds
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
                val propertiesObject = featureObject.getJSONObject("properties")
                val geometryObject = featureObject.getJSONObject("geometry")
                val coordinatesArray = geometryObject.getJSONArray("coordinates")

                val generatedId = UUID.randomUUID().toString()

                val properties = CampgroundProperties(
                    id = generatedId,
                    name = propertiesObject.getString("name"),
                    description = propertiesObject.optString("description", null),
                    link = propertiesObject.optString("link", null),
                    image_url = propertiesObject.optString("image_url", null)
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
                val marker = mMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(campground.properties.name)
                    // Snippet can be set here if not using a custom info window or as a fallback
                )
                marker?.tag = campground // Store the full campground data for the info window
            }
        }
        Log.d("MapsActivity", "Added ${campgrounds.size} campground markers to the map.")
    }
}