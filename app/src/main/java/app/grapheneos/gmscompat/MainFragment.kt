package app.grapheneos.gmscompat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import app.grapheneos.gmscompat.Constants.PLAY_SERVICES_PKG
import app.grapheneos.gmscompat.Constants.PLAY_STORE_PKG

class MainFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(ctx)

        screen.addPref().apply {
            title = getString(R.string.usage_guide)
            val uri = Uri.parse(USAGE_GUIDE_URL)
            linkToActivity(Intent(Intent.ACTION_VIEW, uri))
        }
        screen.addPref().apply {
            title = getString(R.string.component_system_settings, getString(R.string.play_services))
            linkToAppSettings(PLAY_SERVICES_PKG)
        }
        screen.addPref().apply {
            title = getString(R.string.gms_network_location_opt_in)
            val i = Intent("com.google.android.gms.location.settings.LOCATION_ACCURACY")
            i.setClassName(PLAY_SERVICES_PKG, "com.google.android.gms.location.settings.LocationAccuracyActivity")
            linkToActivity(i)
        }
        if (isPkgInstalled(ctx, PLAY_STORE_PKG)) {
            val playStore = getString(R.string.play_store)
            // no public activity to show its preferences
    //        screen.addPref().apply {
    //            title = getString(R.string.component_settings, playStore)
    //            linkToActivity(Intent().setClassName(PLAY_STORE_PKG, "?"))
    //        }
            screen.addPref().apply {
                title = getString(R.string.component_system_settings, playStore)
                linkToAppSettings(PLAY_STORE_PKG)
            }
        }
        screen.addPref().apply {
            title = getString(R.string.google_settings)
            linkToActivity(Intent().setClassName(PLAY_SERVICES_PKG, PLAY_SERVICES_PKG +
                ".app.settings.GoogleSettingsLink"))
//                ".app.settings.GoogleSettingsIALink")
//                ".app.settings.GoogleSettingsActivity")
        }

        preferenceScreen = screen
    }

    fun PreferenceScreen.addPref(): Preference {
        val pref = Preference(context)
        pref.isSingleLineTitle = false
        addPreference(pref)
        return pref
    }

    fun Preference.linkToActivity(intent: Intent) {
        setOnPreferenceClickListener {
            startActivity(intent)
            true
        }
    }

    fun Preference.linkToAppSettings(pkg: String) {
        linkToActivity(appSettingsIntent(pkg))
    }
}

fun appSettingsIntent(pkg: String): Intent {
    val uri = Uri.fromParts("package", pkg, null)
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
}
