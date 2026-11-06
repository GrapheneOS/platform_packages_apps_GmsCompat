package app.grapheneos.gmscompat.configui.gmscore

import android.Manifest
import android.app.compat.gms.GmsCorePackageFlag
import android.app.compat.gms.GmsUtils
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.GosPackageState
import android.ext.PackageId
import android.ext.settings.ExtSettings
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.navigation.NavController
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import app.grapheneos.gmscompat.APP_INFO_ITEM_PERMISSIONS
import app.grapheneos.gmscompat.BaseGlifFragment
import app.grapheneos.gmscompat.BaseGlifPreferenceFragment
import app.grapheneos.gmscompat.BaseSetupFlowActivity
import app.grapheneos.gmscompat.GlifPage
import app.grapheneos.gmscompat.GlifPageBullet
import app.grapheneos.gmscompat.GlifPageSection
import app.grapheneos.gmscompat.R
import app.grapheneos.gmscompat.appSettingsIntent
import app.grapheneos.gmscompat.configui.addCategory
import app.grapheneos.gmscompat.configui.createFooterPreference
import app.grapheneos.gmscompat.gmsCoreHasPermission
import app.grapheneos.gmscompat.navigateSetupFlow
import app.grapheneos.gmscompat.pressBack
import app.grapheneos.gmscompat.renderGlifPage
import com.android.internal.gmscompat.GmsInfo.PACKAGE_GMS_CORE
import com.android.settingslib.widget.LayoutPreference
import com.google.android.setupcompat.template.FooterBarMixin
import com.google.android.setupcompat.template.FooterButton
import com.google.android.setupdesign.GlifLayout
import com.google.android.setupdesign.GlifPreferenceLayout
import com.google.android.setupdesign.view.BulletPointView
import kotlinx.serialization.Serializable

@Serializable
private data object FindHubIntroductionScreen

@Serializable
private data object FindHubRequiredPermissionsScreen

@Serializable
private data object FindHubRecoverableKeystoreScreen

@Serializable
private data object FindHubAdditionalPermissionsScreen

@Serializable
private data object FindHubFinishSetupScreen

private const val FIND_HUB_PACKAGE = "com.google.android.apps.adm"

private val FIND_HUB_NEARBY_DEVICES_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_ADVERTISE,
)

private fun gmsCoreHasFindHubNearbyDevicesPermission(): Boolean =
    FIND_HUB_NEARBY_DEVICES_PERMISSIONS.all(::gmsCoreHasPermission)

class GmsCoreFindHubSetupActivity : BaseSetupFlowActivity() {
    override fun createNavigationGraph(controller: NavController) =
        controller.createGraph(startDestination = FindHubIntroductionScreen) {
            fragment<GmsCoreFindHubIntroductionFragment, FindHubIntroductionScreen> {
                label = getString(R.string.find_hub_setup_title)
            }
            fragment<GmsCoreFindHubRequiredPermissionsFragment, FindHubRequiredPermissionsScreen> {
                label = getString(R.string.find_hub_required_permissions_title)
            }
            fragment<GmsCoreRecoverableKeystoreFragment, FindHubRecoverableKeystoreScreen> {
                label = getString(R.string.gmscore_recover_keystore_access_title)
            }
            fragment<
                GmsCoreFindHubAdditionalPermissionsFragment,
                FindHubAdditionalPermissionsScreen,
            > {
                label = getString(R.string.find_hub_additional_permissions_title)
            }
            fragment<GmsCoreFindHubFinishSetupFragment, FindHubFinishSetupScreen> {
                label = getString(R.string.find_hub_finish_setup_title)
            }
        }
}

class GmsCoreFindHubIntroductionFragment : BaseGlifFragment() {
    override fun createGlifLayout(context: Context): GlifLayout =
        renderGlifPage(
            context,
            GlifPage(
                R.string.find_hub_setup_title,
                R.string.find_hub_setup_intro_summary,
                listOf(
                    GlifPageSection(
                        R.string.find_hub_setup_helps_category,
                        listOf(
                            bullet(
                                R.string.find_hub_setup_enroll_title,
                                R.string.find_hub_setup_enroll_summary,
                            ),
                            bullet(
                                R.string.find_hub_setup_add_devices_title,
                                R.string.find_hub_setup_add_devices_summary,
                            ),
                            bullet(
                                R.string.find_hub_setup_help_network_title,
                                R.string.find_hub_setup_help_network_summary,
                            ),
                            bullet(
                                R.string.find_hub_setup_unknown_tracker_alerts_title,
                                R.string.find_hub_setup_unknown_tracker_alerts_summary,
                            ),
                        ),
                    ),
                    GlifPageSection(
                        R.string.find_hub_setup_keep_in_mind_category,
                        listOf(
                            bullet(
                                R.string.find_hub_setup_complete_with_google_title,
                                R.string.find_hub_setup_complete_with_google_summary,
                            )
                        ),
                    ),
                ),
            ),
        )

    override fun onGlifViewCreated(layout: GlifLayout, savedInstanceState: Bundle?) {
        layout.setFindHubFooter(
            R.string.find_hub_next_button,
            FooterButton.ButtonType.NEXT,
            { navigateSetupFlow(FindHubRequiredPermissionsScreen) },
            R.string.find_hub_cancel_button,
            FooterButton.ButtonType.CANCEL,
            { requireActivity().finish() },
        )
    }

    private fun bullet(@StringRes title: Int, @StringRes summary: Int) =
        GlifPageBullet(title, getText(summary), R.drawable.ic_bullet_point)
}

class GmsCoreFindHubRequiredPermissionsFragment : BaseGlifPreferenceFragment() {
    private lateinit var recoverableKeystorePreference: Preference
    private lateinit var nextButton: FooterButton

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        recoverableKeystorePreference = Preference(context).apply {
            setTitle(R.string.gmscore_recover_keystore_access_title)
            isSingleLineTitle = false
            setOnPreferenceClickListener {
                navigateSetupFlow(FindHubRecoverableKeystoreScreen)
                true
            }
        }
        screen.addPreference(recoverableKeystorePreference)
        screen.addPreference(createFooterPreference().apply {
            setTitle(R.string.find_hub_unknown_tracker_alerts_footer)
        })
        preferenceScreen = screen
    }

    override fun onGlifPreferenceViewCreated(
        layout: GlifPreferenceLayout,
        savedInstanceState: Bundle?,
    ) {
        layout.setHeaderText(R.string.find_hub_required_permissions_title)
        layout.setDescriptionText(R.string.find_hub_required_permissions_summary)
        layout.setDividerInsets(Int.MAX_VALUE, 0)
        nextButton = layout.setFindHubFooter(
            R.string.find_hub_next_button,
            FooterButton.ButtonType.NEXT,
            {
                if (updatePermissionState()) {
                    navigateSetupFlow(FindHubAdditionalPermissionsScreen)
                }
            },
            R.string.find_hub_back_button,
            FooterButton.ButtonType.CLEAR,
            { pressBack() },
        )
        updatePermissionState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    private fun updatePermissionState(): Boolean {
        val enabled = hasRecoverableKeystoreAccess()
        recoverableKeystorePreference.setSummary(
            if (enabled) {
                R.string.find_hub_recovery_keystore_on_summary
            } else {
                R.string.find_hub_recovery_keystore_off_summary
            }
        )
        if (::nextButton.isInitialized) {
            nextButton.isEnabled = enabled
        }
        return enabled
    }

    private fun hasRecoverableKeystoreAccess(): Boolean =
        GosPackageState.get(
            PackageId.GMS_CORE_NAME,
            requireContext().user,
        ).hasPackageFlag(
            GmsCorePackageFlag.GRANT_PERMS_FOR_RECOVER_KEYSTORE_GMSCORE
        )
}

class GmsCoreFindHubAdditionalPermissionsFragment : BaseGlifPreferenceFragment() {
    private lateinit var nearbyDevicesPreference: Preference
    private lateinit var locationPreference: Preference
    private lateinit var networkPreference: Preference
    private lateinit var nextButton: FooterButton

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(
            Preference(context).apply {
                setTitle(R.string.find_hub_open_play_services_app_info)
                setSummary(R.string.find_hub_open_play_services_app_info_summary)
                isSingleLineTitle = false
                intent = appSettingsIntent(PACKAGE_GMS_CORE, APP_INFO_ITEM_PERMISSIONS)
            }
        )

        val statusCategory = screen.addCategory(R.string.find_hub_permission_status_category)
        nearbyDevicesPreference = statusCategory.addPermissionStatusPreference(
            R.string.find_hub_nearby_devices_title
        )
        locationPreference = statusCategory.addPermissionStatusPreference(
            R.string.find_hub_location_status_title
        )
        networkPreference = statusCategory.addPermissionStatusPreference(
            R.string.find_hub_network_title
        )

        val detailsCategory = screen.addCategory(R.string.find_hub_permission_details_category)
        detailsCategory.addPreference(
            SetupDesignBulletPreference(
                context,
                R.string.find_hub_why_register_title,
            ).apply {
                setSummaryText(context.getText(R.string.find_hub_why_register_summary))
            }
        )
        detailsCategory.addPreference(
            SetupDesignBulletPreference(
                context,
                R.string.find_hub_finish_tracker_state_title,
            ).apply {
                // The Find Hub specification says its service can relay an activation request
                // through the network. Play services automatically handles interaction
                // instructions returned by SpotReportingService.UploadScans. The response
                // provides a dedicated key used to authenticate the command, which the tracker
                // verifies. The server's decision criteria are not exposed in the client:
                // https://developers.google.com/nearby/fast-pair/specifications/extensions/fmdn#unwanted-tracking-prevention
                //
                // The cross-platform unwanted tracker specification permits another person to
                // start a sound only while the tracker is separated from its owner. See section
                // 3.13.4:
                // https://www.ietf.org/archive/id/draft-ietf-dult-accessory-protocol-00.html#section-3.13.4
                // Play services also limits manual scan results to separated trackers and
                // rejects this action when its check finds the tracker back near its owner.
                //
                // On a user build, background scanning with Nearby devices allowed and precise
                // Location set to “Allow all the time” produced these logs when Play services
                // 26.24.34 (260400-938041327), version code 262434035, found a registered tracker
                // back near its owner and turned the mode off:
                //
                //     I AdvertisedStateHandler: (REDACTED) Detected owned device with unwanted tracking protection mode: %s
                //     I DeUnwantedTrackingIntOp: Deactivated unwanted tracking mode successfully.
                setSummaryText(context.getText(R.string.find_hub_finish_tracker_state_summary))
            }
        )
        detailsCategory.addPreference(
            SetupDesignBulletPreference(
                context,
                R.string.find_hub_setup_unknown_tracker_alerts_title,
            ).apply {
                setSummaryText(
                    context.getText(R.string.find_hub_unknown_tracker_alerts_permissions_summary)
                )
            }
        )
        detailsCategory.addPreference(
            SetupDesignBulletPreference(
                context,
                R.string.find_hub_nearby_devices_title,
            ).apply {
                // GmsCore can remotely change the locator model list that does not skip Android
                // bond creation through [com.google.android.gms.nearby]
                // fast_pair_locator_tags_model_ids_to_bond. Keep the support warning independent
                // of the installed GmsCore version. Do not describe Nearby devices as enabling
                // powered-off finding: those NearbyManager APIs remain behind
                // BLUETOOTH_PRIVILEGED, and the state query returns disabled when the GmsCompat
                // permission fallback handles the denial.
                setSummaryText(context.getText(R.string.find_hub_nearby_devices_summary))
            }
        )
        detailsCategory.addPreference(
            SetupDesignBulletPreference(
                context,
                R.string.find_hub_location_title,
            ).apply {
                setSummaryText(context.getText(R.string.find_hub_location_summary))
            }
        )
        preferenceScreen = screen
    }

    override fun onGlifPreferenceViewCreated(
        layout: GlifPreferenceLayout,
        savedInstanceState: Bundle?,
    ) {
        layout.setHeaderText(R.string.find_hub_additional_permissions_title)
        layout.setDescriptionText(R.string.find_hub_additional_permissions_summary)
        layout.setDividerInsets(Int.MAX_VALUE, 0)
        nextButton = layout.setFindHubFooter(
            R.string.find_hub_next_button,
            FooterButton.ButtonType.NEXT,
            {
                if (updatePermissionState()) {
                    navigateSetupFlow(FindHubFinishSetupScreen)
                }
            },
            R.string.find_hub_back_button,
            FooterButton.ButtonType.CLEAR,
            { pressBack() },
        )
        updatePermissionState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    private fun updatePermissionState(): Boolean {
        nearbyDevicesPreference.updatePermissionStatus(
            R.string.find_hub_recommended_permission_status_summary,
            permissionStatus(*FIND_HUB_NEARBY_DEVICES_PERMISSIONS),
            R.string.find_hub_nearby_devices_status_summary,
        )
        locationPreference.updatePermissionStatus(
            R.string.find_hub_recommended_permission_status_summary,
            permissionStatus(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ),
            R.string.find_hub_location_status_summary,
        )
        val hasNetwork = gmsCoreHasPermission(Manifest.permission.INTERNET)
        networkPreference.isVisible = !hasNetwork
        if (!hasNetwork) {
            networkPreference.updatePermissionStatus(
                R.string.find_hub_required_permission_status_summary,
                R.string.find_hub_permission_not_allowed,
                R.string.find_hub_network_status_summary,
            )
        }
        if (::nextButton.isInitialized) {
            nextButton.isEnabled = hasNetwork
        }
        return hasNetwork
    }

    @StringRes
    private fun permissionStatus(vararg permissions: String): Int {
        val grantedCount = permissions.count(::gmsCoreHasPermission)
        return when (grantedCount) {
            permissions.size -> R.string.find_hub_permission_allowed
            0 -> R.string.find_hub_permission_not_allowed
            else -> R.string.find_hub_permission_partly_allowed
        }
    }

    private fun permissionSummary(
        @StringRes format: Int,
        @StringRes status: Int,
        @StringRes description: Int,
    ): String =
        getString(
            format,
            getString(status),
            getString(description),
        )

    private fun Preference.updatePermissionStatus(
        @StringRes format: Int,
        @StringRes status: Int,
        @StringRes consequence: Int,
    ) {
        summary = permissionSummary(format, status, consequence)
        setIcon(
            when (status) {
                R.string.find_hub_permission_allowed -> R.drawable.ic_permission_allowed
                R.string.find_hub_permission_partly_allowed -> R.drawable.ic_pending_action
                else -> R.drawable.ic_configuration_required
            }
        )
    }

    private fun PreferenceGroup.addPermissionStatusPreference(
        @StringRes title: Int,
    ): Preference = Preference(context).also {
        it.setTitle(title)
        it.isSingleLineTitle = false
        it.isSelectable = false
        addPreference(it)
    }
}

class GmsCoreFindHubFinishSetupFragment : BaseGlifPreferenceFragment() {
    private lateinit var warningsCategory: PreferenceCategory
    private lateinit var bluetoothOffWarningPreference: Preference
    private lateinit var bluetoothAutoOffWarningPreference: Preference
    private lateinit var locationOffWarningPreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(
            Preference(context).apply {
                setTitle(R.string.find_hub_open_play_store_title)
                setSummary(R.string.find_hub_open_play_store_summary)
                isSingleLineTitle = false
                intent = GmsUtils.createAppPlayStoreIntent(FIND_HUB_PACKAGE)
            }
        )

        warningsCategory = PreferenceCategory(context).apply {
            setTitle(R.string.find_hub_check_settings_category)
            isVisible = false
        }
        screen.addPreference(warningsCategory)
        bluetoothOffWarningPreference = Preference(context).apply {
            setTitle(R.string.find_hub_bluetooth_off_warning_title)
            setSummary(R.string.find_hub_bluetooth_off_warning_summary)
            setIcon(R.drawable.ic_configuration_required)
            intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            isVisible = false
        }
        warningsCategory.addPreference(bluetoothOffWarningPreference)

        bluetoothAutoOffWarningPreference = Preference(context).apply {
            setTitle(R.string.find_hub_bluetooth_auto_off_warning_title)
            setSummary(
                if (context.user.isSystem) {
                    R.string.find_hub_bluetooth_auto_off_warning_summary
                } else {
                    R.string.find_hub_bluetooth_auto_off_warning_secondary_user_summary
                }
            )
            setIcon(R.drawable.ic_configuration_required)
            isSelectable = false
            isVisible = false
        }
        warningsCategory.addPreference(bluetoothAutoOffWarningPreference)

        locationOffWarningPreference = Preference(context).apply {
            setTitle(R.string.find_hub_location_off_warning_title)
            setSummary(R.string.find_hub_location_off_warning_summary)
            setIcon(R.drawable.ic_configuration_required)
            intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            isVisible = false
        }
        warningsCategory.addPreference(locationOffWarningPreference)
        screen.addPreference(
            SetupDesignBulletPreference(
                context,
                R.string.find_hub_finish_add_tracker_title,
            ).apply {
                setSummaryText(context.getText(R.string.find_hub_finish_add_tracker_summary))
            }
        )

        screen.addPreference(
            SetupDesignBulletPreference(
                context,
                R.string.find_hub_finish_settings_title,
            ).apply {
                setSummaryText(context.getText(R.string.find_hub_finish_settings_summary))
            }
        )

        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    override fun onGlifPreferenceViewCreated(
        layout: GlifPreferenceLayout,
        savedInstanceState: Bundle?,
    ) {
        layout.setHeaderText(R.string.find_hub_finish_setup_title)
        layout.setDescriptionText(R.string.find_hub_finish_setup_summary)
        layout.setDividerInsets(Int.MAX_VALUE, 0)
        layout.setFindHubFooter(
            R.string.find_hub_done_button,
            FooterButton.ButtonType.DONE,
            { requireActivity().finish() },
            R.string.find_hub_back_button,
            FooterButton.ButtonType.CLEAR,
            { pressBack() },
        )
        updateState()
    }

    private fun updateState() {
        val context = requireContext()
        val hasNearbyDevices = gmsCoreHasFindHubNearbyDevicesPermission()
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        // GmsCore cannot scan in BLE-only mode while Bluetooth is off. That mode requires full
        // BLUETOOTH_PRIVILEGED.
        bluetoothOffWarningPreference.isVisible =
            hasNearbyDevices && bluetoothAdapter != null && !bluetoothAdapter.isEnabled
        bluetoothAutoOffWarningPreference.isVisible =
            hasNearbyDevices && ExtSettings.BLUETOOTH_AUTO_OFF.get(context) != 0

        val locationManager = context.getSystemService(LocationManager::class.java)
        locationOffWarningPreference.isVisible =
            locationManager?.isLocationEnabled == false

        warningsCategory.isVisible =
            bluetoothOffWarningPreference.isVisible ||
                bluetoothAutoOffWarningPreference.isVisible ||
                locationOffWarningPreference.isVisible
    }
}

// Use the same SetupDesign view as the introduction page for bullets inside preference screens.
private class SetupDesignBulletPreference private constructor(
    context: Context,
    private val bulletView: BulletPointView,
) : LayoutPreference(context, bulletView) {
    constructor(context: Context, @StringRes title: Int) : this(context, BulletPointView(context)) {
        bulletView.setTitle(context.getText(title))
        bulletView.setIcon(context.getDrawable(R.drawable.ic_bullet_point))
        bulletView.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        isSelectable = false
    }

    fun setSummaryText(summary: CharSequence) {
        bulletView.setSummary(summary)
    }
}

private fun GlifLayout.setFindHubFooter(
    @StringRes primaryText: Int,
    @FooterButton.ButtonType primaryType: Int,
    onPrimaryClick: () -> Unit,
    @StringRes secondaryText: Int,
    @FooterButton.ButtonType secondaryType: Int,
    onSecondaryClick: () -> Unit,
): FooterButton {
    val primaryButton = FooterButton.Builder(context)
        .setText(primaryText)
        .setListener { onPrimaryClick() }
        .setButtonType(primaryType)
        .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary)
        .build()
    val secondaryButton = FooterButton.Builder(context)
        .setText(secondaryText)
        .setListener { onSecondaryClick() }
        .setButtonType(secondaryType)
        .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Secondary)
        .build()

    getMixin(FooterBarMixin::class.java).apply {
        this.primaryButton = primaryButton
        this.secondaryButton = secondaryButton
    }
    return primaryButton
}
