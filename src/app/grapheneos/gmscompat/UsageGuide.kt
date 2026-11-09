package app.grapheneos.gmscompat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.navigation.ActivityNavigator
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

class UsageGuideWrapperFragment : BaseCollapsingToolbarFragment() {
    override fun createPreferenceFragment() = UsageGuideFragment()
}

class UsageGuideFragment : SettingsBasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(
            Preference(context).apply {
                setTitle(R.string.read_full_usage_guide)
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(USAGE_GUIDE_URL))
            }
        )
        screen.addPreference(
            Preference(context).apply {
                setTitle(R.string.find_hub)
                setSummary(R.string.find_hub_usage_guide_summary)
                isSingleLineTitle = false
                setOnPreferenceClickListener {
                    findNavController().navigate(
                        NavRoute.FindHubSetup,
                        null,
                        ActivityNavigator.Extras.Builder()
                            // Discard an interrupted guide flow so its separate task starts over.
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK,
                            )
                            .build(),
                    )
                    true
                }
            }
        )

        preferenceScreen = screen
    }
}
