package com.example.fastenroute

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.fastenroute.ui.theme.FastRouteTheme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.google.gson.Gson
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.services.account.MapplsAccountManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FastRouteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    var keysLoaded by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            try {
                                val client = OkHttpClient()
                                val request = Request.Builder()
                                    .url("https://faint-termite-3662.sravanku018.deno.net")
                                    .build()
                                
                                val response = client.newCall(request).execute()
                                val responseBody = response.body?.string()
                                
                                if (response.isSuccessful && responseBody != null) {
                                    val keys = Gson().fromJson(responseBody, Map::class.java)
                                    val restAPIKey = keys["restAPIKey"] as? String
                                    val mapSDKKey = keys["mapSDKKey"] as? String
                                    val atlasClientId = keys["atlasClientId"] as? String
                                    val atlasClientSecret = keys["atlasClientSecret"] as? String
                                    
                                    if (restAPIKey != null && mapSDKKey != null && atlasClientId != null && atlasClientSecret != null) {
                                        MapplsAccountManager.getInstance().restAPIKey = restAPIKey
                                        MapplsAccountManager.getInstance().mapSDKKey = mapSDKKey
                                        MapplsAccountManager.getInstance().atlasClientId = atlasClientId
                                        MapplsAccountManager.getInstance().atlasClientSecret = atlasClientSecret
                                        
                                        // Initialize Mappls with the fetched keys
                                        Mappls.getInstance(applicationContext)
                                        
                                        withContext(Dispatchers.Main) {
                                            keysLoaded = true
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    if (keysLoaded) {
                        MapScreen()
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
