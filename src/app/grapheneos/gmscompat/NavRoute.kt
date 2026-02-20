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
    data object PlayServicesConfig : NavRoute(), DeepLink {
        const val basePath = "gmscompat://playservicesconfig"
        override fun createIntent() = Intent().apply {
            setClass(App.ctx(), MainActivity::class.java)
            putExtra(EXTRA_KEY_ROUTE, basePath)
        }

        fun parseRoute(extras: Bundle?): PlayServicesConfig? {
            return if (extras?.getString(EXTRA_KEY_ROUTE, "") == basePath) {
                PlayServicesConfig
            } else {
                null
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
