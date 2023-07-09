package app.grapheneos.gmscompat

import android.os.BinderDef
import com.android.internal.gmscompat.IClientOfGmsCore2Gca
import com.android.internal.gmscompat.dynamite.server.IFileProxyService

object BinderClientOfGmsCore2Gca : IClientOfGmsCore2Gca.Stub() {

    override fun maybeGetBinderDef(callerPkg: String, processState: Int, ifaceName: String): BinderDef? {
        return BinderDefs.maybeGetBinderDef(callerPkg, processState, ifaceName, false)
    }

    override fun getDynamiteFileProxyService(): IFileProxyService = BinderGms2Gca.dynamiteFileProxyService!!

    override fun showMissingAppNotification(pkgName: String) {
        val prompt = when (pkgName) {
            "com.google.android.tts" -> R.string.missing_speech_services
            else -> throw IllegalArgumentException(pkgName)
        }

        Notifications.handleMissingApp(Notifications.CH_MISSING_APP, App.ctx().getText(prompt), pkgName)
    }
}

