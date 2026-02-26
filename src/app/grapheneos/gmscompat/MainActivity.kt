package app.grapheneos.gmscompat

import android.app.compat.gms.GmsCompat
import android.content.Intent
import android.ext.PackageId
import android.net.Uri
import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.EdgeToEdgeUtils
import com.android.settingslib.collapsingtoolbar.SettingsTransitionActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.navigateUp

const val USAGE_GUIDE_URL = "https://grapheneos.org/usage#sandboxed-google-play"

// MainActivity will have no collapsing toolbar; the Fragments will
class MainActivity : SettingsTransitionActivity() {

    fun getNavController(): NavController? {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                as? NavHostFragment ?: return null
        return navHostFragment.navController
    }

    private val Intent.isLaunchedFromHistory: Boolean
        get() = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

    override fun onNewIntent(intent: Intent) {
        // This is possibly used as we have android:launchMode="singleInstance".
        // However, for PendingIntents of the MainActivity, we generally use
        // Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK, so this is likely not
        // called. If Intent.FLAG_ACTIVITY_CLEAR_TASK wasn't there, this would be relevant.
        // This is for completeness purposes.
        super.onNewIntent(intent)
        setIntent(intent)
        val navController = getNavController() ?: return
        val route = NavRoute.findRoute(intent.extras) ?: return
        navController.apply {
            val startRoute = graph.startDestinationRoute
            if (startRoute != null) {
                popBackStack(startRoute, inclusive = false)
            } else {
                popBackStack<NavRoute.Main>(inclusive = false)
            }
            navigateWithAnimation(route)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        EdgeToEdgeUtils.enable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        if (!GmsCompat.isEnabledFor(PackageId.GMS_CORE_NAME, userId)) {
            val uri = Uri.parse(USAGE_GUIDE_URL)
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            finishAndRemoveTask()
            return
        }

        val navController = getNavController()!!
        navController.apply {
            graph = GmsCompatNavGraph.create(this)

            /*
            Unfortunately, the following does not work, because collapsingtoolbar's action_bar is
            android.widget.Toolbar, not androidx.appcompat.widget.Toolbar

            val toolbar = findViewById<Toolbar>(com.android.settingslib.collapsingtoolbar.R.id.action_bar)
            NavigationUI.setupWithNavController(
                collapsingToolbarLayout,
                toolbar,
                this,
                AppBarConfiguration(graph, null),
            )
            */
        }

        if (savedInstanceState == null && !intent.isLaunchedFromHistory) {
            NavRoute.findRoute(intent.extras)?.let { route ->
                navController.navigateWithAnimation(route)
            }
        }
    }

    override fun onNavigateUp(): Boolean {
        if (getNavController()?.navigateUp(null) != true) {
            finishAfterTransition()
        }
        return true
    }
}
