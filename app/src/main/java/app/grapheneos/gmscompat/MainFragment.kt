package app.grapheneos.gmscompat

import android.Manifest.permission
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import app.grapheneos.gmscompat.Const.PLAY_SERVICES_PKG
import app.grapheneos.gmscompat.Const.PLAY_STORE_PKG
import java.lang.Exception
import java.lang.StringBuilder

class MainFragment : PreferenceFragmentCompat() {
    lateinit var potentialIssuesCategory: PreferenceCategory

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(ctx)

        screen.addPref().apply {
            title = getString(R.string.usage_guide)
            intent = Intent(Intent.ACTION_VIEW, Uri.parse(USAGE_GUIDE_URL))
        }
        screen.addPref().apply {
            title = getString(R.string.component_system_settings, getString(R.string.play_services))
            intent = appSettingsIntent(PLAY_SERVICES_PKG)
        }
        SwitchPreferenceCompat(ctx).apply {
            title = getString(R.string.reroute_location_requests_to_os_apis)
            isSingleLineTitle = false
            isChecked = PrefsProvider.isRedirectionEnabled(Redirections.ID_GoogleLocationManagerService)
            setOnPreferenceChangeListener { _, value ->
                val redirectionEnabled = value as Boolean
                PrefsProvider.setRedirectionState(
                    Redirections.ID_GoogleLocationManagerService,
                    redirectionEnabled
                )

                var msg: String? = null
                if (redirectionEnabled) {
                    // other location modes imply this one
                    if (playServicesHasPermission(permission.ACCESS_COARSE_LOCATION)) {
                        msg = getString(R.string.revoke_location_permission)
                    }
                } else {
                    if (!playServicesHasFullLocationPermission()) {
                        msg = getString(R.string.play_services_no_location_permission, ctx.packageManager.backgroundPermissionOptionLabel)
                    }
                }
                if (msg != null) {
                    AlertDialog.Builder(ctx).apply {
                        setMessage(msg)
                        setPositiveButton(R.string.play_services_settings) { _, _ ->
                            startFreshActivity(appSettingsIntent(PLAY_SERVICES_PKG))
                        }
                        show()
                    }
                }
                updatePotentialIssues()
                true
            }
            screen.addPreference(this)
        }
        screen.addPref().apply {
            title = getString(R.string.gms_network_location_opt_in)
            val i = Intent("com.google.android.gms.location.settings.LOCATION_ACCURACY")
            i.setClassName(PLAY_SERVICES_PKG, "com.google.android.gms.location.settings.LocationAccuracyActivity")
            intent = freshActivity(i)
        }
        if (isPkgInstalled(PLAY_STORE_PKG)) {
            val playStore = getString(R.string.play_store)
            screen.addPref().apply {
                title = getString(R.string.component_system_settings, playStore)
                intent = appSettingsIntent(PLAY_STORE_PKG)
            }
        }
        screen.addPref().apply {
            title = getString(R.string.google_settings)
            intent = freshActivity(Intent().setClassName(PLAY_SERVICES_PKG, PLAY_SERVICES_PKG +
                ".app.settings.GoogleSettingsLink"))
//                ".app.settings.GoogleSettingsIALink")
//                ".app.settings.GoogleSettingsActivity")
        }
        potentialIssuesCategory = PreferenceCategory(ctx).apply {
            title = getString(R.string.potential_issues)
            screen.addPreference(this)
        }
        preferenceScreen = screen
    }

    private fun updatePotentialIssues() {
        val cat = potentialIssuesCategory
        cat.removeAll()
        val ctx = cat.context

        val locationIssues = geolocationIssues(ctx)
        if (locationIssues != null) {
            cat.addDialogPref(locationIssues).apply {
                title = getString(R.string.geolocation)
            }
        }
        if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(PLAY_SERVICES_PKG)) {
            val d = AlertDialog.Builder(ctx).apply {
                setMessage(R.string.play_services_no_battery_exemption)
                setPositiveButton(R.string.play_services_settings) { _, _ ->
                    startFreshActivity(playServicesSettings())
                }
            }
            cat.addDialogPref(d).apply {
                title = getString(R.string.push_notifications)
            }
        }
        val playStoreIssues = playStoreIssues(ctx)
        if (playStoreIssues != null) {
            cat.addDialogPref(playStoreIssues).apply {
                title = getString(R.string.play_store)
            }
        }
        cat.isVisible = cat.preferenceCount != 0
    }

    private fun PreferenceGroup.addDialogPref(dialog: AlertDialog.Builder): Preference {
        return addPref().apply {
            setOnPreferenceClickListener {
                dialog.show()
                true
            }
        }
    }

    private fun geolocationIssues(ctx: Context): AlertDialog.Builder? {
        val sb = StringBuilder(1000)
        var addEnableLocationButton = false
        if (!ctx.getSystemService(LocationManager::class.java).isLocationEnabled) {
            sb.separator()
            sb.resString(R.string.location_access_is_off_for_all_apps)
            addEnableLocationButton = true
        }
        val psHasAnyLocationPerm = playServicesHasPermission(permission.ACCESS_COARSE_LOCATION)

        var addPsSettingsButton = false
        var addAlwaysOnScanningSettingsButton = false
        var addSelfSettingsButton = false

        if (PrefsProvider.isRedirectionEnabled(Redirections.ID_GoogleLocationManagerService)) {
            if (ctx.checkSelfPermission(permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                sb.separator()
                sb.append(getString(R.string.missing_location_permission, ctx.packageManager.backgroundPermissionOptionLabel))
                addSelfSettingsButton = true
            }
            if (psHasAnyLocationPerm) {
                sb.separator()
                sb.resString(R.string.location_redirection_extra_permissions)
                addPsSettingsButton = true
            }
        } else {
            if (!playServicesHasFullLocationPermission()) {
                sb.separator()
                sb.append(getString(R.string.play_services_no_location_permission, ctx.packageManager.backgroundPermissionOptionLabel))
                addPsSettingsButton = true
            }
            try {
                val uri = Uri.parse("content://com.google.settings/partner/network_location_opt_in")
                val proj = arrayOf("value")
                val cursor = ctx.contentResolver.query(uri, proj, null, null)
                cursor?.use {
                    if (cursor.count == 1 && cursor.columnCount == 1) {
                        cursor.moveToPosition(0)
                        val enabled = intToBool(cursor.getInt(0))
                        if (!enabled) {
                            sb.separator()
                            sb.append(getString(R.string.no_gms_network_location,
                                getString(R.string.gms_network_location_opt_in),
                                getString(R.string.reroute_location_requests_to_os_apis)))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val psHasBtScanPerm = playServicesHasPermission(permission.BLUETOOTH_SCAN)
            if (!psHasBtScanPerm) {
                sb.separator()
                sb.resString(R.string.play_services_no_ble_location)
                addPsSettingsButton = true
            }
            val cr = ctx.contentResolver
            sb.separator()
            if (intToBool(Settings.Global.getInt(cr, "wifi_scan_always_enabled", -1))) {
                if (psHasAnyLocationPerm) {
                    sb.resString(R.string.always_on_wifi_scanning_allowed)
                } else {
                    sb.resString(R.string.always_on_wifi_scanning_enabled_but_disallowed)
                    addPsSettingsButton = true
                }
            } else {
                sb.resString(R.string.always_on_wifi_scanning_not_allowed)
            }
            sb.separator()
            if (intToBool(Settings.Global.getInt(cr, "ble_scan_always_enabled", -1))) {
                if (psHasBtScanPerm) {
                    sb.resString(R.string.always_on_bluetooth_scanning_allowed)
                } else {
                    sb.resString(R.string.always_on_bluetooth_scanning_enabled_but_disallowed)
                    addPsSettingsButton = true
                }
            } else {
                sb.resString(R.string.always_on_bluetooth_scanning_disabled)
            }
            addAlwaysOnScanningSettingsButton = true
        }
        if (sb.length == 0) {
            return null
        }
        return AlertDialog.Builder(ctx).apply {
            setMessage(sb.toString())
            if (addPsSettingsButton) {
                setPositiveButton(R.string.play_services_settings) { _, _ ->
                    startFreshActivity(playServicesSettings())
                }
            }
            if (addAlwaysOnScanningSettingsButton) {
                setNeutralButton(R.string.always_on_scanning_settings) { _, _ ->
                    startFreshActivity(Intent("android.settings.LOCATION_SCANNING_SETTINGS"))
                }
            } else if (addSelfSettingsButton) {
                setNeutralButton(R.string.open_settings) { _, _ ->
                    startFreshActivity(appSettingsIntent(Const.PKG_NAME))
                }
            }
            if (addEnableLocationButton) {
                setNegativeButton(R.string.location_settings) {_, _ ->
                    startFreshActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
        }
    }

    private fun playStoreIssues(ctx: Context): AlertDialog.Builder? {
        if (!isPkgInstalled(PLAY_STORE_PKG)) {
            return null
        }
        var addSettingsLink = false
        var addPlayGamesLink = false

        val sb = StringBuilder(1000)

        if (!playStoreHasPermission(permission.WRITE_EXTERNAL_STORAGE)) {
            sb.separator()
            sb.resString(R.string.play_store_obb_permission)
            addSettingsLink = true
        }

        val playGames = "com.google.android.play.games"
        if (!isPkgInstalled(playGames)) {
            sb.separator()
            sb.resString(R.string.no_play_games)
            addPlayGamesLink = true
        }
        if (sb.length == 0) {
            return null
        }
        return AlertDialog.Builder(ctx).apply {
            setMessage(sb.toString())
            if (addSettingsLink) {
                setPositiveButton(R.string.play_store_settings) { _, _ ->
                    startFreshActivity(appSettingsIntent(PLAY_STORE_PKG))
                }
            }
            if (addPlayGamesLink) {
                setNegativeButton(R.string.install_play_games) {_, _ ->
                    val uri = Uri.parse("market://details?id=$playGames")
                    val i = Intent(Intent.ACTION_VIEW, uri)
                    i.setPackage(PLAY_STORE_PKG)
                    startFreshActivity(i)
                }
            }
        }
    }

    private fun StringBuilder.resString(id: Int) {
        append(resources.getString(id))
    }

    private fun StringBuilder.separator() {
        if (length != 0) {
            append("\n\n")
        }
        append("• ")
    }

    override fun onResume() {
        super.onResume()
        updatePotentialIssues()
    }

    fun PreferenceGroup.addPref(): Preference {
        val pref = Preference(context)
        pref.isSingleLineTitle = false
        addPreference(pref)
        return pref
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // otherwise items blink when they are updated
        listView.itemAnimator = null
    }

    fun startFreshActivity(intent: Intent) {
        startActivity(freshActivity(intent))
    }
}

private fun freshActivity(intent: Intent): Intent {
    // needed to ensure consistent behavior,
    // otherwise existing instance that is in unknown state could be shown
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    return intent
}

fun playServicesSettings() = appSettingsIntent(PLAY_SERVICES_PKG)

fun appSettingsIntent(pkg: String): Intent {
    val uri = Uri.fromParts("package", pkg, null)
    return freshActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
}

fun playServicesHasFullLocationPermission(): Boolean {
    return playServicesHasPermission(permission.ACCESS_BACKGROUND_LOCATION)
            // will crash with COARSE permission when trying to access cell network IDs, see TelephonyManager.requestCellInfoUpdate()
            && playServicesHasPermission(permission.ACCESS_FINE_LOCATION)
}

private fun intToBool(v: Int) =
    if (v == 1) {
        true
    } else {
        if (Const.DEV) {
            require(v == 0)
        }
        false
    }
