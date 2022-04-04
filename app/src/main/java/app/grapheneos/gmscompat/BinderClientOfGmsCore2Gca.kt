package app.grapheneos.gmscompat

import com.android.internal.gmscompat.BinderRedirector
import com.android.internal.gmscompat.IClientOfGmsCore2Gca
import com.android.internal.gmscompat.dynamite.server.IFileProxyService

object BinderClientOfGmsCore2Gca : IClientOfGmsCore2Gca.Stub() {
    override fun getRedirectableInterfaces(): Array<String> = Redirections.getInterfaces()
    override fun getBinderRedirector(id: Int): BinderRedirector = Redirections.getRedirector(id)

    override fun getDynamiteFileProxyService(): IFileProxyService = BinderGms2Gca.dynamiteFileProxyService!!
}
