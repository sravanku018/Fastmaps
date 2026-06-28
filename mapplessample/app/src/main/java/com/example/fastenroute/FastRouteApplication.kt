package com.example.fastenroute

import android.app.Application
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.services.account.MapplsAccountManager

class FastRouteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Keys are now fetched dynamically in MainActivity
    }
}
