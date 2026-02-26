package app.grapheneos.gmscompat

import android.Manifest.permission
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PackageInfoFlags
import android.ext.PackageId
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.gmscompat.GmsCompatApp
import com.android.internal.gmscompat.GmsInfo.PACKAGE_GMS_CORE
import java.util.concurrent.Executors

private val bgExecutor = Executors.newSingleThreadExecutor()

class MainWrapperFragment : BaseCollapsingToolbarFragment() {
    override fun createPreferenceFragment() = MainFragment()
}

class MainFragment : SettingsBasePreferenceFragment() {
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
            intent = appSettingsIntent(PackageId.GMS_CORE_NAME)
        }

        if (checkPackageId(PackageId.GMS_CORE_NAME, PackageId.GMS_CORE)) {
            screen.addPref().apply {
                setTitle(R.string.play_services_special_permissions)
                setSummary(R.string.play_services_special_permissions_summary)
                setOnPreferenceClickListener {
                    val route = NavRoute.PlayServicesConfig.parseRoute(activity?.intent?.extras)
                        ?: NavRoute.PlayServicesConfig()
                    navigateWithAnimation(route)
                    true
                }
            }
        }

        if (checkPackageId(PackageId.PLAY_STORE_NAME, PackageId.PLAY_STORE)) {
            val playStore = getString(R.string.play_store)
            screen.addPref().apply {
                title = getString(R.string.component_system_settings, playStore)
                intent = appSettingsIntent(PackageId.PLAY_STORE_NAME)
            }
        }

        if (checkPackageId(PackageId.ANDROID_AUTO_NAME, PackageId.ANDROID_AUTO)) {
            screen.addPref().apply {
                setTitle(R.string.android_auto)
                setOnPreferenceClickListener {
                    navigateWithAnimation(NavRoute.AndroidAutoConfig)
                    true
                }
            }
        }

        screen.addPref().apply {
            title = getString(R.string.google_settings)
            val i = Intent("com.android.settings.action.EXTRA_SETTINGS")
            i.setPackage(PACKAGE_GMS_CORE)
            intent = freshActivity(i)
        }
        PreferenceCategory(ctx).apply {
            title = getString(R.string.geolocation)
            screen.addPreference(this)

            SwitchPreferenceCompat(ctx).apply {
                title = getString(R.string.reroute_location_requests_to_os_apis)
                isSingleLineTitle = false
                isChecked = BinderDefs.isEnabled(BinderDefGroup.LOCATION)
                setOnPreferenceChangeListener { _, value ->
                    val redirectionEnabled = value as Boolean
                    BinderDefs.setEnabled(BinderDefGroup.LOCATION, redirectionEnabled)

                    var msg: String? = null
                    if (redirectionEnabled) {
                        // other location modes imply this one
                        if (gmsCoreHasPermission(permission.ACCESS_COARSE_LOCATION)) {
                            msg = getString(R.string.revoke_location_permission)
                        }
                    } else {
                        if (!gmsCoreHasFullLocationPermission()) {
                            msg = getString(R.string.play_services_no_location_permission, ctx.packageManager.backgroundPermissionOptionLabel)
                        }
                    }
                    if (msg != null) {
                        AlertDialog.Builder(ctx).apply {
                            setMessage(msg)
                            setPositiveButton(R.string.play_services_settings) { _, _ ->
                                startFreshActivity(appSettingsIntent(PACKAGE_GMS_CORE))
                            }
                            show()
                        }
                    }
                    updatePotentialIssues()
                    true
                }
                addPreference(this)
            }

            screen.addPref().apply {
                title = getString(R.string.gms_network_location_opt_in)
                val i = Intent("com.google.android.gms.location.settings.LOCATION_ACCURACY")
                i.setPackage(PACKAGE_GMS_CORE)
                intent = freshActivity(i)
            }

            screen.addPreference(this)
        }

        potentialIssuesCategory = PreferenceCategory(ctx).apply {
            title = getString(R.string.potential_issues)
            isVisible = false
            screen.addPreference(this)
        }
        preferenceScreen = screen
    }

    private fun updatePotentialIssues() {
        val ctx: Context = requireContext()
        bgExecutor.execute {
            val geolocationIssues = geolocationIssues(ctx)

            ctx.mainExecutor.execute {
                updatePotentialIssuesInner(geolocationIssues)
            }
        }
    }

    private fun updatePotentialIssuesInner(locationIssues: AlertDialog.Builder?) {
        val cat = potentialIssuesCategory
        cat.removeAll()
        val ctx = cat.context

        if (locationIssues != null) {
            cat.addDialogPref(locationIssues).apply {
                title = getString(R.string.geolocation)
            }
        }
        if (!ctx.getSystemService(PowerManager::class.java)!!.isIgnoringBatteryOptimizations(PACKAGE_GMS_CORE)) {
            val d = AlertDialog.Builder(ctx).apply {
                setMessage(R.string.play_services_no_battery_exemption)
                setPositiveButton(R.string.play_services_settings) { _, _ ->
                    startFreshActivity(gmsCoreSettings())
                }
            }
            cat.addDialogPref(d).apply {
                title = getString(R.string.push_notifications)
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
        if (!ctx.getSystemService(LocationManager::class.java)!!.isLocationEnabled) {
            sb.separator()
            sb.resString(R.string.location_access_is_off_for_all_apps)
            addEnableLocationButton = true
        }
        val psHasAnyLocationPerm = gmsCoreHasPermission(permission.ACCESS_COARSE_LOCATION)

        var addPsSettingsButton = false
        var addAlwaysOnScanningSettingsButton = false
        var addSelfSettingsButton = false

        if (BinderDefs.isEnabled(BinderDefGroup.LOCATION)) {
            if (psHasAnyLocationPerm) {
                sb.separator()
                sb.resString(R.string.location_redirection_extra_permissions)
                addPsSettingsButton = true
            }
        } else {
            val hasFullLocationPermission = gmsCoreHasFullLocationPermission()
            if (!hasFullLocationPermission) {
                sb.separator()
                sb.append(getString(R.string.play_services_no_location_permission, ctx.packageManager.backgroundPermissionOptionLabel))
                addPsSettingsButton = true
            }
            var gmsNetworkLocationEnabled = false
            try {
                val uri = Uri.parse("content://com.google.settings/partner/network_location_opt_in")
                val proj = arrayOf("value")
                val cursor = ctx.contentResolver.query(uri, proj, null, null)
                cursor?.use {
                    if (cursor.count == 1 && cursor.columnCount == 1) {
                        cursor.moveToPosition(0)
                        gmsNetworkLocationEnabled = intToBool(cursor.getInt(0))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (!gmsNetworkLocationEnabled) {
                sb.separator()
                sb.append(getString(R.string.no_gms_network_location,
                    getString(R.string.gms_network_location_opt_in),
                    getString(R.string.reroute_location_requests_to_os_apis)))
            }

            val appOpsManager = ctx.getSystemService(AppOpsManager::class.java)!!
            val packageManager = ctx.packageManager
            val gmsCoreUid = packageManager.getPackageUid(PACKAGE_GMS_CORE, PackageInfoFlags.of(0L))

            // CHANGE_WIFI_STATE permission is always granted (it's a non-runtime permission), but
            // OP_CHANGE_WIFI_STATE app-op, which guards a subset of methods that are also guarded
            // by this permission (eg Wi-Fi scanning), can be revoked by the user
            val gmsCoreHasChangeWifiStateAppOp = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_CHANGE_WIFI_STATE, gmsCoreUid,
                    PACKAGE_GMS_CORE) == AppOpsManager.MODE_ALLOWED

            if (!gmsCoreHasChangeWifiStateAppOp) {
                sb.separator()
                sb.resString(R.string.play_services_no_wifi_control_appop)
            }

            val psHasBtScanPerm = gmsCoreHasPermission(permission.BLUETOOTH_SCAN)
            if (!psHasBtScanPerm) {
                sb.separator()
                sb.resString(R.string.play_services_no_ble_location)
                addPsSettingsButton = true
            }
            val cr = ctx.contentResolver
            if (hasFullLocationPermission && !intToBool(Settings.Global.getInt(cr, "wifi_scan_always_enabled", -1))) {
                sb.separator()
                sb.resString(R.string.always_on_wifi_scanning_disabled)
                addAlwaysOnScanningSettingsButton = true
            }

            if (psHasBtScanPerm && !intToBool(Settings.Global.getInt(cr, "ble_scan_always_enabled", -1))) {
                sb.separator()
                sb.resString(R.string.always_on_bluetooth_scanning_disabled)
                addAlwaysOnScanningSettingsButton = true
            }
        }
        if (sb.length == 0) {
            return null
        }
        return AlertDialog.Builder(ctx).apply {
            setMessage(sb.toString())
            if (addPsSettingsButton) {
                setPositiveButton(R.string.play_services_settings) { _, _ ->
                    startFreshActivity(gmsCoreSettings())
                }
            }
            if (addAlwaysOnScanningSettingsButton) {
                setNeutralButton(R.string.always_on_scanning_settings) { _, _ ->
                    startFreshActivity(Intent("android.settings.LOCATION_SCANNING_SETTINGS"))
                }
            } else if (addSelfSettingsButton) {
                setNeutralButton(R.string.open_app_info) { _, _ ->
                    startFreshActivity(appSettingsIntent(GmsCompatApp.PKG_NAME))
                }
            }
            if (addEnableLocationButton) {
                setNegativeButton(R.string.location_settings) {_, _ ->
                    startFreshActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
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

fun gmsCoreHasFullLocationPermission(): Boolean {
    return gmsCoreHasPermission(permission.ACCESS_BACKGROUND_LOCATION)
            // will crash with COARSE permission when trying to access cell network IDs, see TelephonyManager.requestCellInfoUpdate()
            && gmsCoreHasPermission(permission.ACCESS_FINE_LOCATION)
}

private fun intToBool(v: Int) =
    if (v == 1) {
        true
    } else {
        false
    }
