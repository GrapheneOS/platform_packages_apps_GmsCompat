package app.grapheneos.gmscompat.configui.aauto

import android.Manifest
import android.app.compat.gms.AndroidAutoPackageFlag
import android.app.compat.gms.GmsUtils
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.ServiceInfo
import android.ext.PackageId
import android.net.Uri
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import app.grapheneos.gmscompat.BaseCollapsingToolbarFragment
import app.grapheneos.gmscompat.getAppInfoOrNull
import app.grapheneos.gmscompat.R
import app.grapheneos.gmscompat.configui.BaseGosPkgStateConfigFragment
import app.grapheneos.gmscompat.configui.addCategory
import app.grapheneos.gmscompat.configui.addPref

class AndroidAutoConfigWrapperFragment : BaseCollapsingToolbarFragment() {
    override fun createPreferenceFragment() = AndroidAutoConfigFragment()
}

class AndroidAutoConfigFragment : BaseGosPkgStateConfigFragment(
    packageName = PackageId.ANDROID_AUTO_NAME,
    titleStringRes = R.string.android_auto
) {
    lateinit var aautoSettingsPref: Preference
    lateinit var potentialIssues: PreferenceGroup
    lateinit var aautoVoiceCommandIssues: Preference

    override fun configurePreferenceScreen(screen: PreferenceScreen) {
        aautoSettingsPref = screen.addPref(getText(R.string.aauto_settings)).apply {
            intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES).apply {
                `package` = packageName
            }
        }

        screen.addCategory(R.string.permissions_category).apply {
            addPkgFlagPerm(
                this, AndroidAutoPackageFlag.GRANT_PERMS_FOR_WIRED_ANDROID_AUTO,
                R.string.aauto_wired_perms_title,
                R.string.aauto_wired_perms_confirm,
            )
            addPkgFlagPerm(
                this, AndroidAutoPackageFlag.GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO,
                R.string.aauto_wireless_perms_title,
                R.string.aauto_wireless_perms_confirm,
            )
            addPkgFlagPerm(
                this, AndroidAutoPackageFlag.GRANT_AUDIO_ROUTING_PERM,
                R.string.audio_routing_perm_title,
                R.string.audio_routing_perm_confirm,
            )
            addPkgFlagPerm(
                this, AndroidAutoPackageFlag.GRANT_PERMS_FOR_ANDROID_AUTO_PHONE_CALLS,
                R.string.aauto_phone_perm_title,
                R.string.aauto_phone_perm_confirm,
            )

            addPref(getText(R.string.aauto_app_info_title)).apply {
                setSummary(R.string.aauto_app_info_summary)
                intent = createAppInfoIntent(packageName)
            }

            addPref(getText(R.string.notif_listener_settings_title)).apply {
                setSummary(R.string.notif_listener_settings_summary)
                intent = getNotifListenerSettingsIntent()
            }
        }

        potentialIssues = screen.addCategory(R.string.potential_issues_category).apply {
            addPref(getText(R.string.aauto_issue_voice_commands)).let {
                aautoVoiceCommandIssues = it
            }
        }

        screen.addCategory(R.string.optional_deps_category).apply {
            addAppPref("com.google.android.apps.maps", getText(R.string.google_maps_app))
            addAppPref("com.google.android.tts", getText(R.string.speech_services_app))
            addAppPref(PackageId.G_SEARCH_APP_NAME, getText(R.string.google_search_app))
        }
    }

    private fun createAppInfoIntent(pkgName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkgName, null)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private fun PreferenceGroup.addAppPref(pkgName: String, title: CharSequence): Preference {
        return addPref(title).apply {
            packagePrefs.put(pkgName, this)
        }
    }

    override fun updateNonPkgStateUi(applicationInfo: ApplicationInfo) {
        aautoSettingsPref.apply {
            isEnabled = applicationInfo.enabled
            if (applicationInfo.enabled) {
                summary = null
            } else {
                setSummary(R.string.aauto_settings_summary_disabled)
            }
        }

        aautoVoiceCommandIssues.apply {
            val text = getVoiceCommandIssuesText()
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

        potentialIssues.isVisible = aautoVoiceCommandIssues.isVisible

        packagePrefs.entries.forEach { e ->
            val pkgName = e.key
            val pref = e.value

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
    }

    private fun getVoiceCommandIssuesText(): CharSequence? {
        val list = getVoiceCommandIssues()
        if (list.isEmpty()) {
            return null
        }
        return getString(R.string.aauto_issue_voice_commands_header) + "\n\n" +
                list.map {"• " + getString(it) }.joinToString("\n")
    }

    private fun getVoiceCommandIssues(): List<Int> {
        val list = arrayListOf<Int>()

        if (pkgManager.checkPermission(Manifest.permission.RECORD_AUDIO, PackageId.ANDROID_AUTO_NAME) != PERMISSION_GRANTED) {
            list += R.string.aauto_issue_aauto_no_microphone_perm
        }

        val gsaName = PackageId.G_SEARCH_APP_NAME
        val gsaAppInfo = pkgManager.getAppInfoOrNull(gsaName)

        var gsaInstalled = false

        if (gsaAppInfo != null && gsaAppInfo.ext().packageId == PackageId.G_SEARCH_APP) {
            val src = pkgManager.getInstallSourceInfo(gsaName)
            gsaInstalled = src.initiatingPackageName == PackageId.PLAY_STORE_NAME
        }

        if (!gsaInstalled) {
            list += R.string.aauto_issue_gsa_not_installed
            return list
        }

        if (!gsaAppInfo!!.enabled) {
            list += R.string.aauto_issue_gsa_disabled
        }

        if (pkgManager.checkPermission(Manifest.permission.INTERNET, gsaName) != PERMISSION_GRANTED) {
            list += R.string.aauto_issue_gsa_no_network_perm
        }

        if (pkgManager.checkPermission(Manifest.permission.RECORD_AUDIO, gsaName) != PERMISSION_GRANTED) {
            list += R.string.aauto_issue_gsa_no_microphone_perm
        }

        return list
    }

    private fun getNotifListenerSettingsIntent(): Intent? {
        val intent = Intent(NotificationListenerService.SERVICE_INTERFACE).apply {
            `package` = PackageId.ANDROID_AUTO_NAME
        }

        val notifListenerServices = pkgManager.queryIntentServices(intent, 0)
        if (notifListenerServices.size != 1) {
            return null
        }

        val nls: ServiceInfo = notifListenerServices.first().serviceInfo

        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            val nlsComponent = ComponentName(nls.packageName, nls.name)
            putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, nlsComponent.flattenToString())
        }
    }
}
