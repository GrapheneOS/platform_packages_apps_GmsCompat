package app.grapheneos.gmscompat

import android.app.Notification
import android.app.PendingIntent
import android.app.compat.gms.GmsCompat
import android.content.Context
import android.content.Intent
import android.content.pm.GosPackageState
import android.content.pm.GosPackageStateFlag
import android.credentials.CredentialManager
import android.ext.PackageId
import android.ext.SettingsIntents
import android.ext.settings.app.AswBlockPlayIntegrityApi
import android.net.Uri
import android.os.Binder
import android.os.BinderDef
import android.provider.Settings
import android.util.Log
import androidx.core.content.getSystemService
import com.android.internal.gmscompat.IClientOfGmsCore2Gca
import com.android.internal.gmscompat.fileservice.IFileProxyService

object BinderClientOfGmsCore2Gca : IClientOfGmsCore2Gca.Stub() {

    override fun maybeGetBinderDef(callerPkg: String, processState: Int, ifaceName: String): BinderDef? {
        return BinderDefs.maybeGetBinderDef(callerPkg, processState, ifaceName, false)
    }

    override fun getGmsCoreFileProxyService(): IFileProxyService = BinderGms2Gca.gmsCoreFileProxyService!!

    override fun showMissingAppNotification(pkgName: String) {
        val prompt = when (pkgName) {
            "com.google.android.tts" -> R.string.missing_speech_services
            else -> throw IllegalArgumentException(pkgName)
        }

        Notifications.handleMissingApp(Notifications.CH_MISSING_APP, App.ctx().getText(prompt), pkgName)
    }

    override fun showPlayIntegrityNotification(pkgName: String, isBlocked: Boolean) {
        val ctx = App.ctx()
        if (ctx.packageManager.getPackageUid(pkgName, 0) != Binder.getCallingUid()) {
            // this method should be called by app itself
            throw SecurityException()
        }

        val TAG = "showPlayIntegrityNotification"
        Log.d(TAG, "caller: $pkgName")

        val setting = AswBlockPlayIntegrityApi.I
        val gosPs = GosPackageState.get(pkgName, ctx.userId)
        if (!setting.isNotificationEnabled(gosPs)) {
            // there's a client-side isNotificationEnabled() check before the call
            Log.e(TAG, "notification is disabled")
            return
        }

        Notifications.builder(Notifications.CH_MANAGE_PLAY_INTEGRITY_API).run {
            setSmallIcon(R.drawable.ic_info)
            setContentTitle(R.string.notif_app_used_play_integrity_api_title)
            val text = if (isBlocked) R.string.notif_app_play_integrity_api_blocked
                else R.string.notif_app_used_play_integrity_api
            setContentText(ctx.getString(text, getApplicationLabel(ctx, pkgName)))
            run {
                val intent = SettingsIntents.getAppIntent(ctx, SettingsIntents.APP_MANAGE_PLAY_INTEGRITY_API, pkgName)
                val pendingIntent = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                addAction(Notification.Action.Builder(null,
                    ctx.getText(R.string.notif_app_used_play_integrity_api_action_more_info),
                    pendingIntent).build())
            }
            run {
                val pendingIntent = PendingActionReceiver.makeWriteGosPackageStateAndCancelNotif(
                    ctx, pkgName,
                    GosPackageStateFlag.SUPPRESS_PLAY_INTEGRITY_API_NOTIF, true,
                    Notifications.ID_MANAGE_PLAY_INTEGRITY_API)
                addAction(Notification.Action.Builder(null,
                    ctx.getText(R.string.dont_show_again), pendingIntent).build())
            }
            setShowWhen(true)
            show(Notifications.ID_MANAGE_PLAY_INTEGRITY_API)
        }
    }

    override fun onGoogleIdCredentialOptionInit() {
        val ctx = App.ctx()
        if (!GmsCompat.isEnabledFor(PackageId.GMS_CORE_NAME, ctx.userId)) {
            return
        }

        if (isGoogleIdCredentialProviderEnabled(ctx)) {
            return
        }

        Notifications.builder(Notifications.CH_SIGN_IN_WITH_GOOGLE).run {
            val intent = Intent(Settings.ACTION_CREDENTIAL_PROVIDER, Uri.parse("package:" + PackageId.GMS_CORE_NAME))
            setContentIntent(PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE))

            setSmallIcon(R.drawable.ic_configuration_required)
            setContentTitle(ctx.getText(R.string.sign_in_with_google_notif_title))
            setContentText(ctx.getText(R.string.sign_in_with_google_notif_text))
            setAutoCancel(true)
            show(Notifications.ID_ENABLE_GOOGLE_CREDENTIAL_PROVIDER)
        }
    }

    private fun isGoogleIdCredentialProviderEnabled(ctx: Context): Boolean {
        val credentialM = ctx.getSystemService<CredentialManager>()!!
        // isEnabledCredentialProviderService only allows to query app's own services
        val providers = credentialM.getCredentialProviderServices(ctx.userId,
                CredentialManager.PROVIDER_FILTER_USER_PROVIDERS_ONLY)

        return providers.any {
            it.isEnabled
                    && it.componentName.packageName == PackageId.GMS_CORE_NAME
                    && it.hasCapability("com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL")
        }
    }
}

