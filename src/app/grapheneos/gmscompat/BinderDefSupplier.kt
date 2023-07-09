package app.grapheneos.gmscompat

import android.content.Context
import android.os.Binder
import android.os.BinderDef
import java.util.Arrays
import kotlin.reflect.KClass

abstract class BinderDefSupplier(val ifaceName: String, implClass: KClass<out Binder>) {
    val className = implClass.java.name

    // Path of APK from which to load class that is referred to by className
    open fun apkPath(ctx: Context): String {
        return ctx.applicationInfo.sourceDir
    }

    // Binder transaction codes
    abstract fun transactionCodes(callerPkg: String): IntArray?

    fun get(ctx: Context, callerPkg: String): BinderDef {
        val txnCodes = transactionCodes(callerPkg)
        if (txnCodes != null) {
            Arrays.sort(txnCodes)
        }
        return BinderDef(ifaceName, apkPath(ctx), className, txnCodes)
    }
}
