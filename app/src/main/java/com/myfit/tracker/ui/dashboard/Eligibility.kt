package com.myfit.tracker.ui.dashboard

import com.myfit.tracker.data.db.Sex
import com.myfit.tracker.data.db.UserProfile
import com.myfit.tracker.data.prefs.AppSettings

/**
 * Who sees which health features. Women's cycle tracking only appears for female profiles;
 * blood-sugar tools only for people who said they manage diabetes (or who already switched them on).
 */
@Suppress("UNUSED_PARAMETER")
fun showCycle(profile: UserProfile?, settings: AppSettings): Boolean = profile?.sex == Sex.FEMALE

fun showDiabetes(settings: AppSettings): Boolean = when (settings.diabetesType) {
    "none" -> false
    "unset" -> settings.glucoseEnabled
    else -> true
}

/** True once the person told us they manage diabetes (any type, including prediabetes). */
fun managesDiabetes(type: String): Boolean = type != "unset" && type != "none"

fun diabetesLabel(type: String): String = when (type) {
    "none" -> "No"
    "type1" -> "Type 1"
    "type2" -> "Type 2"
    "gestational" -> "Gestational"
    "prediabetes" -> "Prediabetes"
    "other" -> "Other"
    else -> "Not set"
}
