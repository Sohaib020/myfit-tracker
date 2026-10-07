package com.myfit.tracker.update

import com.myfit.tracker.BuildConfig

/** Where this copy of the app came from, and the link to give people who want to get it. */
object Store {
    val name: String get() = BuildConfig.STORE
    /** Store builds (Galaxy Store / Play) never point people to the APK download site. */
    val isStoreBuild: Boolean get() = BuildConfig.STORE_URL.isNotBlank()
    val appLink: String get() = BuildConfig.STORE_URL.ifBlank { AppUpdater.SITE }
    val label: String get() = when (BuildConfig.STORE) { "galaxy" -> "Galaxy Store"; "play" -> "Google Play"; else -> "the MyFit website" }
}
