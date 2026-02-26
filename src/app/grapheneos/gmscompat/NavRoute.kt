package app.grapheneos.gmscompat

import android.content.Intent
import android.os.Bundle
import kotlinx.serialization.Serializable

/**
 * Use this with a NavController to navigate
 */
sealed class NavRoute {
    sealed interface DeepLink {
        fun createIntent(): Intent
    }

    @Serializable
    data object Main : NavRoute()

    @Serializable
    data object AndroidAutoConfig : NavRoute() {
        const val basePath = "gmscompat://aautoconfig"
    }

    @Serializable
    data class PlayServicesConfig(
        val isIccAuthPotentialIssue: Boolean = false
    ) : NavRoute(), DeepLink {

        override fun createIntent() = Intent().apply {
            setClass(App.ctx(), MainActivity::class.java)
            putExtra(EXTRA_KEY_ROUTE, basePath)
            putExtra(EXTRA_KEY_ICC_AUTH_ISSUE, isIccAuthPotentialIssue)
        }

        companion object {
            const val basePath = "gmscompat://playservicesconfig"
            private const val EXTRA_KEY_ICC_AUTH_ISSUE = "key_icc_auth_show_issue"

            fun parseRoute(extras: Bundle?): PlayServicesConfig? {
                return if (extras?.getString(EXTRA_KEY_ROUTE, "") == basePath) {
                    PlayServicesConfig(extras.getBoolean(EXTRA_KEY_ICC_AUTH_ISSUE, false))
                } else {
                    null
                }
            }
        }
    }

    companion object {
        private const val EXTRA_KEY_ROUTE = "gmscompat.route"

        fun findRoute(extras: Bundle?): NavRoute? {
            extras ?: return null

            PlayServicesConfig.parseRoute(extras)?.let { return it }

            return null
        }
    }
}
