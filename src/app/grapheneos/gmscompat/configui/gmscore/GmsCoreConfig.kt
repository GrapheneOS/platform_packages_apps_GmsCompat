package app.grapheneos.gmscompat.configui.gmscore

import android.app.compat.gms.GmsCorePackageFlag
import android.content.pm.ApplicationInfo
import android.ext.PackageId
import androidx.preference.PreferenceScreen
import app.grapheneos.gmscompat.BaseCollapsingToolbarFragment
import app.grapheneos.gmscompat.R
import app.grapheneos.gmscompat.configui.BaseGosPkgStateConfigFragment
import app.grapheneos.gmscompat.configui.addCategory

class GmsCoreConfigWrapperFragment : BaseCollapsingToolbarFragment() {
    override fun createPreferenceFragment() = GmsCoreConfigFragment()
}

class GmsCoreConfigFragment : BaseGosPkgStateConfigFragment(
    packageName = PackageId.GMS_CORE_NAME,
    titleStringRes = R.string.gmscore_settings
) {
    override fun configurePreferenceScreen(screen: PreferenceScreen) {
        screen.addCategory(R.string.rcs_activation_category).apply {
            addPkgFlagPerm(
                this, GmsCorePackageFlag.GRANT_PERMS_FOR_ICC_AUTHENTICATION,
                R.string.gmscore_icc_auth_perms_title,
                R.string.gmscore_icc_auth_perms_confirm,
            )
        }
    }

    override fun updateNonPkgStateUi(applicationInfo: ApplicationInfo) {}
}
