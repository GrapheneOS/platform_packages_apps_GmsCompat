package app.grapheneos.gmscompat.configui

import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.ext.PackageId
import android.os.Bundle
import android.provider.Telephony
import android.os.Process
import androidx.annotation.StringRes
import app.grapheneos.gmscompat.getAppInfoOrNull

sealed class IssueCheck {

    abstract fun getStringResOfIssues(context: Context, options: Bundle): List<Int>

    class OwnerUser(private val issueStringRes: Int) : IssueCheck() {
        override fun getStringResOfIssues(context: Context, options: Bundle): List<Int> {
            return if (Process.myUserHandle().identifier != 0) {
                listOf(issueStringRes)
            } else {
                emptyList()
            }
        }
    }

    /**
     * Checks [permission] of the config [fragment]'s ][packageName] without any installed or
     * enabled checks.
     */
    class PermissionOnly(
        private val packageName: String,
        private val permission: String,
        @StringRes private val issueStringRes: Int,
    ) : IssueCheck() {
        override fun getStringResOfIssues(context: Context, options: Bundle): List<Int> {
            return if (
                context.packageManager.checkPermission(permission, packageName)
                != PERMISSION_GRANTED
            ) {
                listOf(issueStringRes)
            } else {
                emptyList()
            }
        }
    }

    class App(
        private val packageName: String,
        private val packageId: Int,
        @StringRes private val notInstalledStringRes: Int,
        @StringRes private val notEnabledStringRes: Int,
        /**
         * If this is not set, only [notInstalledStringRes] will be shown for issues if not from
         * Play Store, and no missing permissions info will be processed/shown.
         */
        @StringRes private val notFromPlayStoreStringRes: Int = 0,
        private val appChecks: List<Check> = emptyList(),
    ) : IssueCheck() {

        sealed interface Check {
            val issueStringRes: Int
        }

        class Permission(val permission: String, override val issueStringRes: Int) : Check
        class SmsRole(override val issueStringRes: Int) : Check

        override fun getStringResOfIssues(context: Context, options: Bundle): List<Int> {
            val pm = context.packageManager
            val appInfo = pm.getAppInfoOrNull(packageName)
                ?: return listOf(notInstalledStringRes)

            val isInstalledFromPlayStore: Boolean
            if (appInfo.ext().packageId == packageId) {
                val src = pm.getInstallSourceInfo(packageName)
                isInstalledFromPlayStore = src.initiatingPackageName == PackageId.PLAY_STORE_NAME
            } else {
                return listOf(notInstalledStringRes)
            }

            if (notFromPlayStoreStringRes == 0 && !isInstalledFromPlayStore) {
                return listOf(notInstalledStringRes)
            }

            return buildList {
                if (!appInfo.enabled) {
                    add(notEnabledStringRes)
                }
                if (notFromPlayStoreStringRes != 0 && !isInstalledFromPlayStore) {
                    val skipWarning = options.getBoolean(
                        OPTION_KEY_SKIP_PLAY_STORE_INSTALL_SOURCE_WARNING,
                        false
                    )
                    if (!skipWarning) {
                        add(notFromPlayStoreStringRes)
                    }
                }

                for (permCheck in appChecks) {
                    when (permCheck) {
                        is Permission -> {
                            if (
                                pm.checkPermission(permCheck.permission, packageName)
                                != PERMISSION_GRANTED
                            ) {
                                add(permCheck.issueStringRes)
                            }
                        }
                        is SmsRole -> {
                            if (Telephony.Sms.getDefaultSmsPackage(context) != packageName) {
                                add(permCheck.issueStringRes)
                            }
                        }
                    }
                }
            }
        }

        companion object {
            private const val OPTION_KEY_SKIP_PLAY_STORE_INSTALL_SOURCE_WARNING =
                "skip_playstore_install_warning"

            fun addSkipPlayStoreInstallSourceWarning(options: Bundle) {
                options.putBoolean(OPTION_KEY_SKIP_PLAY_STORE_INSTALL_SOURCE_WARNING, true)
            }
        }
    }
}

fun Collection<IssueCheck>.getAllIssueRes(context: Context, options: Bundle = Bundle.EMPTY) =
    flatMap {
        it.getStringResOfIssues(context, options)
    }
