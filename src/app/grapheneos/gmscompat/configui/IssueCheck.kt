package app.grapheneos.gmscompat.configui

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.ext.PackageId
import android.provider.Telephony
import android.os.Process
import androidx.annotation.StringRes
import app.grapheneos.gmscompat.getAppInfoOrNull

sealed class IssueCheck {

    abstract fun getStringResOfIssues(context: Context, packageManager: PackageManager): List<Int>

    class OwnerUser(private val issueStringRes: Int) : IssueCheck() {
        override fun getStringResOfIssues(
            context: Context,
            packageManager: PackageManager
        ): List<Int> {
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
        override fun getStringResOfIssues(context: Context, packageManager: PackageManager): List<Int> {
            return if (
                packageManager.checkPermission(permission, packageName)
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

        override fun getStringResOfIssues(context: Context, packageManager: PackageManager): List<Int> {
            val appInfo = packageManager.getAppInfoOrNull(packageName)
                ?: return listOf(notInstalledStringRes)

            val isInstalledFromPlayStore: Boolean
            if (appInfo.ext().packageId == packageId) {
                val src = packageManager.getInstallSourceInfo(packageName)
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
                    add(notFromPlayStoreStringRes)
                }

                for (permCheck in appChecks) {
                    when (permCheck) {
                        is Permission -> {
                            if (
                                packageManager.checkPermission(permCheck.permission, packageName)
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
    }
}
