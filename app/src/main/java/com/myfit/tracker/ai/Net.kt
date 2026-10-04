package com.myfit.tracker.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Is there a usable internet connection right now? */
object Net {
    fun online(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
