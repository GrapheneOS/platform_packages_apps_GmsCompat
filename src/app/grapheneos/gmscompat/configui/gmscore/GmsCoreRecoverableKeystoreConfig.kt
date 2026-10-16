package app.grapheneos.gmscompat.configui.gmscore

import android.app.compat.gms.GmsCorePackageFlag
import android.content.Context
import android.content.Intent
import android.content.pm.GosPackageState
import android.ext.PackageId
import android.os.Bundle
import android.permission.PermissionManager
import android.text.Html
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import app.grapheneos.gmscompat.App
import app.grapheneos.gmscompat.BaseGlifFragment
import app.grapheneos.gmscompat.BaseSetupFlowActivity
import app.grapheneos.gmscompat.GlifPage
import app.grapheneos.gmscompat.GlifPageBullet
import app.grapheneos.gmscompat.GlifPageSection
import app.grapheneos.gmscompat.Notifications
import app.grapheneos.gmscompat.R
import app.grapheneos.gmscompat.pressBack
import app.grapheneos.gmscompat.renderGlifPage
import com.google.android.setupcompat.template.FooterBarMixin
import com.google.android.setupcompat.template.FooterButton
import com.google.android.setupdesign.GlifLayout
import com.google.android.setupdesign.template.RequireScrollMixin
import kotlinx.serialization.Serializable

@Serializable
private data object RecoverableKeystoreScreen

class GmsCoreRecoverableKeystoreActivity : BaseSetupFlowActivity() {
    override fun createNavigationGraph(controller: NavController) =
        controller.createGraph(startDestination = RecoverableKeystoreScreen) {
            fragment<GmsCoreRecoverableKeystoreFragment, RecoverableKeystoreScreen> {
                label = getString(
                    R.string.gmscore_recover_keystore_access_title
                )
            }
        }

    companion object {
        fun createIntent() = Intent(App.ctx(), GmsCoreRecoverableKeystoreActivity::class.java)
    }
}

class GmsCoreRecoverableKeystoreFragment : BaseGlifFragment() {

    // Keep the selected layout and its footer behavior on the same permission state snapshot.
    private var recoverableKeystoreAccessEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        recoverableKeystoreAccessEnabled = GosPackageState.get(
            PackageId.GMS_CORE_NAME,
            requireContext().user,
        ).hasPackageFlag(
            GmsCorePackageFlag.GRANT_PERMS_FOR_RECOVER_KEYSTORE_GMSCORE
        )
    }

    override fun createGlifLayout(context: Context): GlifLayout =
        renderGlifPage(
            context,
            if (recoverableKeystoreAccessEnabled) createDisablePage() else createEnablePage(),
        )

    override fun onGlifViewCreated(layout: GlifLayout, savedInstanceState: Bundle?) {
        val actionButton = FooterButton.Builder(layout.context)
            .setText(
                if (recoverableKeystoreAccessEnabled) {
                    R.string.gmscore_recover_keystore_turn_off_button
                } else {
                    R.string.grant_dialog_button_allow
                }
            )
            .setButtonType(
                if (recoverableKeystoreAccessEnabled) {
                    FooterButton.ButtonType.STOP
                } else {
                    FooterButton.ButtonType.OPT_IN
                }
            )
            .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary)
            .build()

        val footerBar = layout.getMixin(FooterBarMixin::class.java)
        footerBar.primaryButton = actionButton
        footerBar.secondaryButton =
            FooterButton.Builder(layout.context)
                .setText(
                    if (recoverableKeystoreAccessEnabled) {
                        R.string.gmscore_recover_keystore_cancel_button
                    } else {
                        R.string.gmscore_recover_keystore_dont_allow_button
                    }
                )
                .setListener { pressBack() }
                .setButtonType(FooterButton.ButtonType.CANCEL)
                .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Secondary)
                .build()
        layout.getMixin(RequireScrollMixin::class.java).requireScrollWithButton(
            layout.context,
            actionButton,
            R.string.gmscore_recover_keystore_more_button,
            {
                if (recoverableKeystoreAccessEnabled) {
                    showDisableAccessDialog()
                } else {
                    showEnableAccessDialog()
                }
            },
        )
    }

    private fun createEnablePage() =
        page(
            R.string.gmscore_recover_keystore_access_title,
            R.string.gmscore_recover_keystore_access_intro_summary,
            section(
                R.string.gmscore_recover_keystore_how_it_works_category,
                bullet(
                    R.string.gmscore_recover_keystore_keys_encrypted_title,
                    R.string.gmscore_recover_keystore_keys_encrypted_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_screen_lock_title,
                    R.string.gmscore_recover_keystore_screen_lock_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_public_key_title,
                    R.string.gmscore_recover_keystore_public_key_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_thm_title,
                    R.string.gmscore_recover_keystore_thm_summary,
                ),
            ),
            section(
                R.string.gmscore_recover_keystore_google_features_category,
                bullet(
                    R.string.gmscore_recover_keystore_find_hub_locations_title,
                    R.string.gmscore_recover_keystore_find_hub_locations_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_passkeys_title,
                    R.string.gmscore_recover_keystore_passkeys_summary,
                ),
            ),
            section(
                R.string.gmscore_recover_keystore_google_access_category,
                bullet(
                    R.string.gmscore_recover_keystore_on_device_title,
                    R.string.gmscore_recover_keystore_on_device_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_on_servers_title,
                    R.string.gmscore_recover_keystore_on_servers_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_during_recovery_title,
                    R.string.gmscore_recover_keystore_during_recovery_summary,
                ),
            ),
            section(
                R.string.gmscore_recover_keystore_keep_in_mind_category,
                bullet(
                    R.string.gmscore_recover_keystore_clear_storage_title,
                    R.string.gmscore_recover_keystore_clear_storage_summary,
                ),
            ),
            section(
                R.string.gmscore_recover_keystore_more_info_category,
                whitepaperBullet(),
                bullet(
                    R.string.gmscore_recover_keystore_access_footer_title,
                    R.string.gmscore_recover_keystore_access_footer,
                ),
            ),
        )

    private fun createDisablePage() =
        page(
            R.string.gmscore_recover_keystore_access_on_title,
            R.string.gmscore_recover_keystore_access_on_intro_summary,
            section(
                R.string.gmscore_recover_keystore_what_access_does_category,
                bullet(
                    R.string.gmscore_recover_keystore_protects_feature_keys_title,
                    R.string.gmscore_recover_keystore_protects_feature_keys_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_uses_screen_lock_title,
                    R.string.gmscore_recover_keystore_uses_screen_lock_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_during_recovery_title,
                    R.string.gmscore_recover_keystore_during_recovery_summary,
                ),
            ),
            section(
                R.string.gmscore_recover_keystore_if_turned_off_category,
                bullet(
                    R.string.gmscore_recover_keystore_new_requests_blocked_title,
                    R.string.gmscore_recover_keystore_new_requests_blocked_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_existing_data_not_deleted_title,
                    R.string.gmscore_recover_keystore_existing_data_not_deleted_summary,
                ),
                bullet(
                    R.string.gmscore_recover_keystore_can_turn_on_again_title,
                    R.string.gmscore_recover_keystore_can_turn_on_again_summary,
                ),
            ),
            section(R.string.gmscore_recover_keystore_more_info_category, whitepaperBullet()),
        )

    private fun page(
        @StringRes header: Int,
        @StringRes description: Int,
        vararg sections: GlifPageSection,
    ) = GlifPage(header, description, sections.asList())

    private fun section(@StringRes title: Int, vararg bullets: GlifPageBullet) =
        GlifPageSection(title, bullets.asList())

    private fun bullet(@StringRes title: Int, @StringRes summary: Int) =
        GlifPageBullet(title, getText(summary), R.drawable.ic_bullet_point)

    private fun whitepaperBullet() =
        GlifPageBullet(
            R.string.gmscore_recover_keystore_whitepaper_title,
            Html.fromHtml(
                getString(R.string.gmscore_recover_keystore_whitepaper_summary),
                Html.FROM_HTML_MODE_LEGACY,
            ),
            R.drawable.ic_info,
        )

    private fun showEnableAccessDialog() {
        AlertDialog.Builder(requireContext()).run {
            setTitle(R.string.gmscore_recover_keystore_access_dialog_title)
            setMessage(R.string.gmscore_recover_keystore_access_dialog_message)
            setPositiveButton(R.string.grant_dialog_button_allow) { _, _ ->
                updateRecoverableKeystoreAccess(true)
            }
            setNegativeButton(android.R.string.cancel, null)
            show()
        }
    }

    private fun showDisableAccessDialog() {
        AlertDialog.Builder(requireContext()).run {
            setTitle(R.string.gmscore_recover_keystore_disable_dialog_title)
            setMessage(R.string.gmscore_recover_keystore_disable_dialog_message)
            setPositiveButton(R.string.gmscore_recover_keystore_disable_dialog_button) { _, _ ->
                updateRecoverableKeystoreAccess(false)
            }
            setNegativeButton(android.R.string.cancel, null)
            show()
        }
    }

    private fun updateRecoverableKeystoreAccess(enabled: Boolean) {
        val userId = android.os.Process.myUserHandle().identifier
        val editor = GosPackageState.edit(PackageId.GMS_CORE_NAME, userId)
        editor.setPackageFlagState(
            GmsCorePackageFlag.GRANT_PERMS_FOR_RECOVER_KEYSTORE_GMSCORE,
            enabled,
        )
        if (!editor.apply()) {
            pressBack()
            return
        }

        requireContext().getSystemService(PermissionManager::class.java)!!
            .updatePermissionStateAndInvalidateCache(PackageId.GMS_CORE_NAME, userId)

        if (enabled) {
            Notifications.cancel(
                Notifications.ID_GMS_CORE_MISSING_RECOVERABLE_KEYSTORE_PERMISSION
            )
            showRestartRequiredDialog()
            return
        }
        pressBack()
    }

    private fun showRestartRequiredDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.gmscore_recover_keystore_restart_dialog_title)
            .setMessage(R.string.gmscore_recover_keystore_restart_dialog_message)
            .setPositiveButton(R.string.gmscore_recover_keystore_restart_dialog_close, null)
            .create()
        dialog.setOnDismissListener { pressBack() }
        dialog.show()
    }
}
