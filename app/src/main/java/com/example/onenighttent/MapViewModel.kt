package com.example.onenighttent

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MapViewModel : ViewModel() {

    private val _campgrounds = MutableLiveData<List<CampgroundFeature>>()
    val campgrounds: LiveData<List<CampgroundFeature>> = _campgrounds

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessages = MutableLiveData<String?>()
    val errorMessages: LiveData<String?> = _errorMessages

    companion object {
        private const val TAG = "com.example.onenighttent.MapViewModel"
        private const val GEOJSON_URL =
            "https://1nitetent.com/app/themes/1nitetent/assets/json/campgrounds.geojson"
    }

    init {
        fetchCampgroundData()
    }

    fun fetchCampgroundData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessages.value = null // Clear previous errors
            Log.d(TAG, "Starting to load campground data from URL...")
            try {
                val data: CampgroundData? = withContext(Dispatchers.IO) {
                    var urlConnection: HttpURLConnection? = null
                    try {
                        val url = URL(GEOJSON_URL)
                        urlConnection = url.openConnection() as HttpURLConnection
                        urlConnection.requestMethod = "GET"
                        urlConnection.connectTimeout = 15000
                        urlConnection.readTimeout = 10000

                        val responseCode = urlConnection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val inputStream = urlConnection.inputStream
                            val reader = InputStreamReader(inputStream)
                            Gson().fromJson(reader, CampgroundData::class.java)
                        } else {
                            Log.e(TAG, "HTTP error code: $responseCode from $GEOJSON_URL")
                            // Post error to be handled by UI
                            withContext(Dispatchers.Main) {
                                _errorMessages.value = "HTTP error: $responseCode"
                            }
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during network request or parsing", e)
                        withContext(Dispatchers.Main) {
                            _errorMessages.value = "Network error: ${e.localizedMessage}"
                        }
                        null
                    } finally {
                        urlConnection?.disconnect()
                    }
                }

                if (data != null) {
                    Log.d(TAG, "Successfully loaded and parsed ${data.features.size} campgrounds.")
                    _campgrounds.value = data.features
                } else {
                    // Error message would have been set by the inner try-catch
                    if (_errorMessages.value == null) { // If no specific error was caught inside
                        _errorMessages.value = "Failed to load or parse campground data."
                    }
                    _campgrounds.value = emptyList() // Clear data on failure
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in fetchCampgroundData coroutine", e)
                _errorMessages.value = "Error loading data: ${e.localizedMessage}"
                _campgrounds.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Call this method if you want to clear a shown error message from the UI
    fun clearErrorMessage() {
        _errorMessages.value = null
    }
}