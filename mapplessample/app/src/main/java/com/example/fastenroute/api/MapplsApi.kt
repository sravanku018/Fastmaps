package com.example.fastenroute.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SuggestResult(
    @SerializedName("suggestedLocations") val suggestions: List<SuggestedLocation>?
)

data class SuggestedLocation(
    @SerializedName("placeName") val placeName: String?,
    @SerializedName("placeAddress") val placeAddress: String?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("eLoc") val eLoc: String?
)

data class RouteResult(
    @SerializedName("routes") val routes: List<Route>?,
    @SerializedName("results") val results: RouteResults?
)

data class RouteResults(
    @SerializedName("routes") val routes: List<Route>?
)

data class Route(
    @SerializedName("distance") val distance: Double?,
    @SerializedName("duration") val duration: Double?,
    @SerializedName("legs") val legs: List<Leg>?
)


data class Leg(
    @SerializedName("steps") val steps: List<Step>?
)

data class Step(
    @SerializedName("geometry") val geometry: String?,
    @SerializedName("maneuvers") val maneuvers: List<Maneuver>?
)

data class Maneuver(
    @SerializedName("location") val location: List<Double>?
)

data class OAuthTokenResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_in") val expiresIn: Long?
)

data class NominatimResult(
    @SerializedName("lat") val lat: String?,
    @SerializedName("lon") val lon: String?,
    @SerializedName("display_name") val displayName: String?
)

object MapplsApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val API_KEY = "6028f7f20d0f77db48c8de02979e8cf3"

    // Atlas OAuth 2.0 credentials
    private const val CLIENT_ID = "96dHZVzsAusVK6FU4fGDDFrrFjNKTicKf9s5uxqepvMUkgNbN7ohQiwG2msyhfGCtBgjyjEtJ6hz-AKhDpnGQA=="
    private const val CLIENT_SECRET = "lrFxI-iSEg9iM76CbYdAoW35RGGg8KRx1Hy8H2__HLHPn_iKQWf_Cf_hp727UXiHUAKVYJ0D-19AgLDvs0K89THliUOw0ksA"

    // Cached OAuth token
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0L

    // Nominatim rate limiting (synchronized)
    private val nominatimLock = Any()
    private var lastNominatimRequest: Long = 0L

    /**
     * Fetch OAuth 2.0 access token from Mappls Atlas.
     * Uses client_credentials grant type.
     */
    private fun getAccessToken(): String? {
        // Return cached token if still valid (with 60s buffer)
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiry - 60_000) {
            return cachedToken
        }

        return try {
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()

            val request = Request.Builder()
                .url("https://outpost.mappls.com/api/security/oauth/token")
                .post(body)
                .build()

            Log.d("MapplsApi", "Fetching OAuth token...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            Log.d("MapplsApi", "OAuth response code: ${response.code}, body: ${responseBody.take(200)}")

            if (response.code != 200) {
                Log.e("MapplsApi", "OAuth error ${response.code}: $responseBody")
                return null
            }

            val tokenResponse = Gson().fromJson(responseBody, OAuthTokenResponse::class.java)
            if (tokenResponse.accessToken != null) {
                cachedToken = tokenResponse.accessToken
                tokenExpiry = now + (tokenResponse.expiresIn ?: 3600) * 1000
                Log.d("MapplsApi", "OAuth token obtained, expires in ${tokenResponse.expiresIn}s")
                cachedToken
            } else {
                Log.e("MapplsApi", "No access_token in OAuth response")
                null
            }
        } catch (e: Exception) {
            Log.e("MapplsApi", "OAuth token fetch failed: ${e.message}", e)
            null
        }
    }

    /**
     * Geocode using Nominatim. Address-first with synchronized rate limiting.
     */
    fun geocodeWithNominatim(placeName: String, address: String): Pair<Double, Double>? {
        synchronized(nominatimLock) {
            // Strategy 1: Just the address (most reliable)
            var result = tryGeocode(address)
            if (result != null) return result

            // Strategy 2: City + State (e.g. "Chikkamagaluru, Karnataka")
            val parts = address.split(",").map { it.trim() }
            if (parts.size >= 2) {
                result = tryGeocode(parts.takeLast(2).joinToString(", "))
                if (result != null) return result
            }

            // Strategy 3: Just the city
            if (parts.size >= 2) {
                result = tryGeocode(parts[parts.size - 2])
            }
            return result
        }
    }

    private fun tryGeocode(query: String): Pair<Double, Double>? {
        return try {
            // Respect Nominatim rate limit: 1 request per 1.5 seconds
            val now = System.currentTimeMillis()
            val timeSinceLastRequest = now - lastNominatimRequest
            if (timeSinceLastRequest < 1500) {
                Thread.sleep(1500 - timeSinceLastRequest)
            }
            lastNominatimRequest = System.currentTimeMillis()

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1"
            Log.d("MapplsApi", "Nominatim URL: $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "FastRouteApp/1.0")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            if (response.code == 429) {
                Log.w("MapplsApi", "Nominatim rate limited, waiting...")
                Thread.sleep(1100) // Respect rate limit
                return null
            }

            if (response.code != 200) {
                Log.e("MapplsApi", "Nominatim error ${response.code}")
                return null
            }

            val results = Gson().fromJson(body, Array<NominatimResult>::class.java)
            val first = results?.firstOrNull()
            if (first?.lat != null && first.lon != null) {
                val lat = first.lat.toDoubleOrNull()
                val lng = first.lon.toDoubleOrNull()
                if (lat != null && lng != null) {
                    Log.d("MapplsApi", "Nominatim geocoded: $lat, $lng for $query")
                    return Pair(lat, lng)
                }
            }
            null
        } catch (e: Exception) {
            Log.e("MapplsApi", "Nominatim geocode failed: ${e.message}", e)
            null
        }
    }

    fun searchPlace(query: String): List<SuggestedLocation> {
        return try {
            val token = getAccessToken()
            if (token == null) {
                Log.e("MapplsApi", "No OAuth token, cannot search")
                return emptyList()
            }

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // Try legacy endpoint with Authorization header
            val url = "https://atlas.mappls.com/api/places/search/json?query=$encodedQuery&region=ind"
            Log.d("MapplsApi", "Search URL: $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            Log.d("MapplsApi", "Search response code: ${response.code}, body: ${body.take(300)}")

            if (response.code != 200) {
                Log.e("MapplsApi", "Search HTTP error ${response.code}: $body")
                // If 401, clear token cache and retry once
                if (response.code == 401) {
                    cachedToken = null
                    tokenExpiry = 0L
                    Log.d("MapplsApi", "Token expired, will retry on next search")
                }
                return emptyList()
            }

            val result = Gson().fromJson(body, SuggestResult::class.java)
            val suggestions = result.suggestions ?: emptyList()
            Log.d("MapplsApi", "Got ${suggestions.size} search results")
            // Return results as-is; geocoding happens on-demand when user selects a result
            suggestions
        } catch (e: Exception) {
            Log.e("MapplsApi", "Search failed: ${e.message}", e)
            emptyList()
        }
    }

    fun getRoutes(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double
    ): List<Route> {
        return try {
            // Use driving profile + alternatives for multiple route options
            val url = "https://apis.mappls.com/advancedmaps/v1/$API_KEY/route_adv/driving/$startLng,$startLat;$endLng,$endLat?geometries=polyline&overview=full&steps=true&alternatives=true"
            Log.d("MapplsApi", "Route URL: $url")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            Log.d("MapplsApi", "Route response code: ${response.code}")

            if (response.code != 200) {
                Log.e("MapplsApi", "Route HTTP error ${response.code}: $body")
                return emptyList()
            }

            val result = Gson().fromJson(body, RouteResult::class.java)
            val routes = result.routes ?: result.results?.routes ?: emptyList()
            Log.d("MapplsApi", "Got ${routes.size} route alternatives")
            routes
        } catch (e: Exception) {
            Log.e("MapplsApi", "Routing failed: ${e.message}", e)
            emptyList()
        }
    }
}
