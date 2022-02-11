package app.grapheneos.gmscompat

import android.os.Bundle

private const val GET_RD_STATE = 0
private const val SET_RD_STATE = 1

private const val KEY_RESULT = "res"
private const val KEY_ENABLED = "enabled"

private const val AUTHORITY = Const.PKG_NAME + ".PrefsProvider"

// needed to acccess main process prefs from the UI process
class PrefsProvider : AbsContentProvider() {

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val id = arg!!.toInt()
        when (Integer.parseInt(method)) {
            GET_RD_STATE -> {
                val res = Bundle(1)
                res.putBoolean(KEY_RESULT, Redirections.isEnabled(id))
                return res
            }
            SET_RD_STATE -> {
                val enabled = extras!!.getBoolean(KEY_ENABLED)
                Redirections.setState(id, enabled)
            }
        }
        return null
    }

    companion object {
        private fun call(method: Int, arg: String?, bundleArg: Bundle? = null): Bundle? {
            return App.ctx().contentResolver.call(AUTHORITY, method.toString(), arg, bundleArg)
        }

        fun isRedirectionEnabled(id: Int): Boolean {
            notMainProcess()
            return call(GET_RD_STATE, id.toString())!!.getBoolean(KEY_RESULT)
        }

        fun setRedirectionState(id: Int, enabled: Boolean) {
            notMainProcess()
            val arg = Bundle(1)
            arg.putBoolean(KEY_ENABLED, enabled)
            call(SET_RD_STATE, id.toString(), arg)
        }
    }
}
