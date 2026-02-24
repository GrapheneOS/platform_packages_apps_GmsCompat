package app.grapheneos.gmscompat.configui.gmscore

import android.Manifest
import android.app.compat.gms.GmsCorePackageFlag
import android.content.pm.ApplicationInfo
import android.ext.PackageId
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import app.grapheneos.gmscompat.BaseCollapsingToolbarFragment
import app.grapheneos.gmscompat.Notifications
import app.grapheneos.gmscompat.R
import app.grapheneos.gmscompat.configui.BaseGosConfigFragment
import app.grapheneos.gmscompat.configui.IssueCheck
import app.grapheneos.gmscompat.configui.addCategory
import app.grapheneos.gmscompat.configui.addPref

private const val CONFIG_PKG_NAME = PackageId.GMS_CORE_NAME

val rcsIssueChecks = listOf(
    IssueCheck.OwnerUser(R.string.rcs_issue_not_owner_user),
    IssueCheck.PermissionOnly(
        CONFIG_PKG_NAME,
        Manifest.permission.READ_PHONE_STATE,
        R.string.rcs_issue_gmscore_no_phone_perm,
    ),
    IssueCheck.PermissionOnly(
        CONFIG_PKG_NAME,
        Manifest.permission.INTERNET,
        R.string.rcs_issue_gmscore_no_internet_perm,
    ),
    IssueCheck.App(
        packageName = PackageId.BUGLE_NAME,
        packageId = PackageId.BUGLE,
        notInstalledStringRes = R.string.rcs_issue_bugle_not_installed,
        notEnabledStringRes = R.string.rcs_issue_bugle_disabled,
        notFromPlayStoreStringRes = R.string.rcs_issue_bugle_not_from_play_store,
        appChecks = listOf(
            IssueCheck.App.SmsRole(R.string.rcs_issue_bugle_not_sms_app),
            IssueCheck.App.Permission(
                Manifest.permission.INTERNET,
                R.string.rcs_issue_bugle_no_internet_perm,
            )
        )
    )
)

class GmsCoreConfigWrapperFragment : BaseCollapsingToolbarFragment() {
    override fun createPreferenceFragment() = GmsCoreConfigFragment()
}

class GmsCoreConfigFragment : BaseGosConfigFragment(
    configuringPkgName = CONFIG_PKG_NAME,
    titleStringRes = R.string.gmscore_settings
) {

    lateinit var rcsPotentialIssues: Preference

    override fun configurePreferenceScreen(screen: PreferenceScreen) {
        screen.addPref(getText(R.string.gmscore_app_info_title)).apply {
            setSummary(R.string.gmscore_app_info_summary)
            intent = createAppInfoIntent(configuringPkgName)
        }

        screen.addCategory(R.string.permissions_category).apply {
            addPkgFlagPerm(
                this, GmsCorePackageFlag.GRANT_PERMS_FOR_ICC_AUTHENTICATION,
                R.string.gmscore_icc_auth_perms_title,
                R.string.gmscore_icc_auth_perms_confirm,
            )
        }

        screen.addCategory(R.string.rcs_activation_category).apply {
            addAppDependencyPref(PackageId.BUGLE_NAME, R.string.google_messages_app)
            rcsPotentialIssues = addPref(getText(R.string.rcs_potential_issues))
        }
    }

    override fun updateNonPkgStateUi(applicationInfo: ApplicationInfo) {
        rcsPotentialIssues.updateWithIssues(R.string.rcs_issue_header, rcsIssueChecks)

        Notifications.unmarkRcsNotificationHandled()
    }
}
