package app.grapheneos.gmscompat.configui

import android.app.compat.gms.GmsUtils
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.GosPackageState
import android.content.pm.PackageManager
import android.ext.PackageId
import android.net.Uri
import android.os.Bundle
import android.os.PatternMatcher
import android.permission.PermissionManager
import android.provider.Settings
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
 * for [GosPackageState] preferences and app dependency preferences. App dependency preferences
 * either direct the user to the Play Store if not installed, or directs the user to configure it.
 *
 * Use [addPkgFlagPerm] to create a switch preference that will toggle a flag for a package flag in
 * [GosPackageState]. These switch preferences are updated automatically and added to [pkgFlagPrefs]
 *
 * To display potential issues, create a Preference and then use [updateWithIssues] with a list of
 * issue checks in the [updateNonPkgStateUi] function on the Preference.
 *
 * Use [packagePrefs] to add preferences for packages that should be listening for package updates.
 */
abstract class BaseGosConfigFragment(
    val configuringPkgName: String,
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
            addDataSchemeSpecificPart(configuringPkgName, PatternMatcher.PATTERN_LITERAL)
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
        GosPackageState.edit(configuringPkgName, userId).run {
            setPackageFlagState(flag, flagValue)
            applyOrPressBack()
        }

        val permManager = ctx.getSystemService(PermissionManager::class.java)!!
        permManager.updatePermissionStateAndInvalidateCache(configuringPkgName, userId)
    }

    protected fun createAppInfoIntent(pkgName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkgName, null)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    protected fun PreferenceGroup.addAppDependencyPref(
        pkgName: String,
        @StringRes title: Int
    ): Preference {
        return addPref(getString(title)).apply {
            packagePrefs.put(pkgName, this)
        }
    }

    /**
     * Updates a preference with issues determined by the given [issueChecks]. If there are issues,
     * the preference will be visible and dialog will show issues when clicked. If no issues,
     * preference will not be visible.
     */
    protected fun Preference.updateWithIssues(
        @StringRes dialogHeader: Int,
        issueChecks: List<IssueCheck>
    ) {
        val text = getIssuesText(dialogHeader, issueChecks)
        isVisible = text != null

        if (text != null) {
            onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
                AlertDialog.Builder(requireContext()).run {
                    setMessage(text)
                    show()
                }
                true
            }
        }
    }

    private fun getIssuesText(
        @StringRes header: Int,
        issueChecks: List<IssueCheck>
    ): CharSequence? {
        val list = issueChecks.flatMap {
            it.getStringResOfIssues(requireContext(), pkgManager)
        }
        if (list.isEmpty()) {
            return null
        }
        return getString(header) + "\n\n" +
                list.joinToString("\n") { "• " + getString(it) }
    }

    /**
     * Handle updating UI state from sources other than [GosPackageState] preferences and app
     * dependency preferences. Those preferences are already updated by the base class.
     * The [applicationInfo] corresponds to [configuringPkgName].
     *
     * Use [updateWithIssues] to update a preference for displaying issues checked by a list of
     * [IssueCheck]
     */
    abstract fun updateNonPkgStateUi(applicationInfo: ApplicationInfo)

    private fun updateUi() {
        val packageAppInfo = pkgManager.getAppInfoOrNull(configuringPkgName)

        if (packageAppInfo == null) {
            pressBack()
            return
        }

        val ps = GosPackageState.get(configuringPkgName, requireContext().user)
        pkgFlagPrefs.entries.forEach {
            it.value.isChecked = ps.hasPackageFlag(it.key)
        }

        packagePrefs.entries.forEach { (pkgName, pref) ->
            val appInfo = pkgManager.getAppInfoOrNull(pkgName)

            if (appInfo == null) {
                if (pkgManager.getAppInfoOrNull(PackageId.PLAY_STORE_NAME)?.ext()?.packageId == PackageId.PLAY_STORE) {
                    pref.intent = GmsUtils.createAppPlayStoreIntent(pkgName)
                    pref.setSummary(R.string.app_dep_missing_summary)
                } else {
                    pref.intent = null
                    pref.setSummary(R.string.app_dep_missing_summary_no_play_store)
                }
            } else {
                pref.intent = createAppInfoIntent(pkgName)
                pref.setSummary(if (appInfo.enabled) R.string.app_dep_installed
                else R.string.app_dep_disabled)
            }
        }

        updateNonPkgStateUi(packageAppInfo)
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
