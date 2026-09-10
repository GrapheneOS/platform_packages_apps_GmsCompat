package app.grapheneos.gmscompat

import android.content.res.Resources
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.android.settingslib.widget.ExpressiveDesignEnabledProvider
import com.google.android.setupdesign.util.ThemeHelper

abstract class BaseSetupFlowActivity : FragmentActivity(), ExpressiveDesignEnabledProvider {
    protected abstract fun createNavigationGraph(controller: NavController): NavGraph

    // These activities always use the expressive SettingsLib preference theme. Tell its
    // preference adapter to add the rounded groups even when SetupWizard partner configuration is
    // unavailable to this standalone flow.
    final override fun isExpressiveDesignEnabled(): Boolean = true

    protected override fun onApplyThemeResource(
        theme: Resources.Theme,
        resid: Int,
        first: Boolean,
    ) {
        super.onApplyThemeResource(theme, resid, first)

        // ThemeHelper replaces the manifest theme. Restore the SettingsLib preference styling
        // after every SetupDesign theme selection.
        theme.applyStyle(R.style.GmsCompatSetupFlowPreferenceThemeOverlay, true)
    }

    // Setup flows are the root of their own task and should not remain in Recents after exit.
    final override fun finish() {
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Match the theme selection in the Settings app's BiometricEnrollBase.
        ThemeHelper.trySetDynamicColor(this)
        if (ThemeHelper.shouldApplyGlifExpressiveStyle(applicationContext)) {
            ThemeHelper.trySetSuwTheme(this)
        }

        setContentView(R.layout.setup_flow_activity)

        val navHost = supportFragmentManager.findFragmentById(R.id.setup_flow_nav_host)
            as NavHostFragment
        val controller = navHost.navController
        controller.graph = createNavigationGraph(controller)
    }
}

internal fun <T : Any> Fragment.navigateSetupFlow(route: T) {
    findNavController().navigate(
        route,
        navOptions {
            // Settings' FingerprintEnrollmentV2Activity uses these SetupDesign animations for
            // fragment transitions. Their Android 12+ variants use SetupWizard's motion curve.
            anim {
                enter = com.google.android.setupdesign.R.anim.sud_slide_next_in
                exit = com.google.android.setupdesign.R.anim.sud_slide_next_out
                popEnter = com.google.android.setupdesign.R.anim.sud_slide_back_in
                popExit = com.google.android.setupdesign.R.anim.sud_slide_back_out
            }
        },
    )
}
