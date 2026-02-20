package app.grapheneos.gmscompat.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.GosPackageState
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PatternMatcher
import android.permission.PermissionManager
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import app.grapheneos.gmscompat.R
import app.grapheneos.gmscompat.getAppInfoOrNull
import app.grapheneos.gmscompat.pressBack

/**
 * Base fragment for specifying a configuration fragment for the given [packageName] with handling
 * for [GosPackageState] preferences.
 *
 * Use [addPkgFlagPerm] to create a switch preference that will toggle a flag for a package flag in
 * [GosPackageState]. These switch preferences are updated automatically and added to [pkgFlagPrefs]
 *
 * Use [packagePrefs] to preferences for packages that should be listening for
 * package updates.
 */
abstract class BaseGosPkgStateConfigFragment(
    protected val packageName: String,
    @StringRes val titleStringRes: Int,
) : SettingsBasePreferenceFragment() {
    protected val pkgFlagPrefs = mutableMapOf<Int, SwitchPreferenceCompat>()
    /**
     * A map of package names to Preferences for packages that should be updated when the
     * corresponding package state changes
     */
    protected val packagePrefs = mutableMapOf<String, Preference>()

    private val pkgChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            updateUi()
        }
    }

    protected lateinit var pkgManager: PackageManager

    override fun onCreatePreferences(savedState: Bundle?, rootKey: String?) {
        @Suppress("DEPRECATION") // see onOptionsItemSelected
        setHasOptionsMenu(true)

        val ctx = requireContext()
        pkgManager = ctx.packageManager

        val screen = preferenceManager.createPreferenceScreen(ctx)

        configurePreferenceScreen(screen)

        IntentFilter().run {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
            packagePrefs.keys.forEach {
                addDataSchemeSpecificPart(it, PatternMatcher.PATTERN_LITERAL)
            }
            addDataSchemeSpecificPart(packageName, PatternMatcher.PATTERN_LITERAL)
            ctx.registerReceiver(pkgChangeReceiver, this)
        }

        preferenceScreen = screen
    }

    /**
     * Add the preferences to display here.
     */
    abstract fun configurePreferenceScreen(screen: PreferenceScreen)

    override fun onDestroy() {
        super.onDestroy()
        requireContext().unregisterReceiver(pkgChangeReceiver)
    }

    override fun onStart() {
        super.onStart()
        updateUi()
    }

    protected fun addPkgFlagPerm(
        dst: PreferenceGroup,
        flag: Int,
        @StringRes title: Int,
        @StringRes confirmationText: Int,
        @StringRes summary: Int = 0
    ): SwitchPreferenceCompat {
        val pref = SwitchPreferenceCompat(dst.context)
        pref.setTitle(title)
        if (summary != 0) {
            pref.setSummary(summary)
        }

        pkgFlagPrefs[flag] = pref

        pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValueB ->
            val newValue = newValueB as Boolean

            val ctx = requireContext()

            if (newValue) {
                AlertDialog.Builder(ctx).run {
                    setMessage(getText(confirmationText))
                    setPositiveButton(R.string.grant_dialog_button_allow) { _, _ ->
                        updatePackageFlag(ctx, flag, true)
                        updateUi()
                    }
                    setNegativeButton(android.R.string.cancel, null)
                    show()
                }
                false
            } else {
                updatePackageFlag(ctx, flag, false)
                true
            }
        }

        dst.addPreference(pref)
        return pref
    }

    private fun updatePackageFlag(ctx: Context, flag: Int, flagValue: Boolean) {
        val userId = android.os.Process.myUserHandle().identifier
        GosPackageState.edit(packageName, userId).run {
            setPackageFlagState(flag, flagValue)
            applyOrPressBack()
        }

        val permManager = ctx.getSystemService(PermissionManager::class.java)!!
        permManager.updatePermissionState(packageName, userId)

        GosPackageState.edit(packageName, userId).run {
            killUidAfterApply()
            applyOrPressBack()
        }

        val isPkgEnabled = pkgManager.getApplicationInfo(packageName, 0).enabled
        if (isPkgEnabled) {
            // this is needed to invalidate cached system_server state
            pkgManager.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
            pkgManager.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0)
        }
    }

    /**
     * Handle updating non-[GosPackageState] UI state here. GosPackageState preferences are already
     * updated by the base class. The [applicationInfo] corresponds to [packageName].
     */
    abstract fun updateNonPkgStateUi(applicationInfo: ApplicationInfo)

    private fun updateUi() {
        val gmsCoreAppInfo = pkgManager.getAppInfoOrNull(packageName)

        if (gmsCoreAppInfo == null) {
            pressBack()
            return
        }

        val ps = GosPackageState.get(packageName, requireContext().user)
        pkgFlagPrefs.entries.forEach {
            it.value.isChecked = ps.hasPackageFlag(it.key)
        }

        updateNonPkgStateUi(gmsCoreAppInfo)
    }

    protected fun GosPackageState.Editor.applyOrPressBack() {
        if (apply()) {
            updateUi()
        } else {
            // apply() fails only if the package is uninstalled
            pressBack()
        }
    }

    // it's not clear how to resolve deprecation warnings for setHasOptionsMenu and onOptionsItemSelected,
    // they are suppressed in upstream fragments that use android.R.id.home too
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            pressBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
