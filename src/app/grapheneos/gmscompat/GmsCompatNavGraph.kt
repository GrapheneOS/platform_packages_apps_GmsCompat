package app.grapheneos.gmscompat

import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.activity
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import app.grapheneos.gmscompat.configui.aauto.AndroidAutoConfigWrapperFragment
import app.grapheneos.gmscompat.configui.gmscore.GmsCoreConfigWrapperFragment
import app.grapheneos.gmscompat.configui.gmscore.GmsCoreRecoverableKeystoreActivity

object GmsCompatNavGraph {
    fun create(
        controller: NavController,
        startDestination: NavRoute = NavRoute.Main,
    ): NavGraph {
        val ctx = App.ctx()
        return controller.createGraph(startDestination = startDestination) {
            fragment<MainWrapperFragment, NavRoute.Main> {
                label = ctx.getString(R.string.activity_name)
            }
            fragment<AndroidAutoConfigWrapperFragment, NavRoute.AndroidAutoConfig> {
                label = ctx.getString(R.string.android_auto)
            }
            fragment<GmsCoreConfigWrapperFragment, NavRoute.PlayServicesConfig> {
                label = ctx.getString(R.string.gmscore_settings)
            }
            activity<NavRoute.PlayServicesRecoverableKeystoreConfig> {
                activityClass = GmsCoreRecoverableKeystoreActivity::class
                label = ctx.getString(R.string.gmscore_recover_keystore_access_title)
            }
        }
    }
}
