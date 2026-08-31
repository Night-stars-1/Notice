package moe.notice.filter.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import moe.notice.filter.BuildConfig
import moe.notice.filter.ModuleStatus
import android.content.Context

class HookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            BuildConfig.APPLICATION_ID -> hookSelf(lpparam)
            "android" -> hookSystemServer(lpparam)
        }
    }

    private fun hookSelf(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                ModuleStatus::class.java.name,
                lpparam.classLoader,
                "isActive",
                XC_MethodReplacement.returnConstant(true),
            )
        } catch (t: Throwable) {
            XposedBridge.log("Notice: hook self failed: $t")
        }
    }

    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val nms = XposedHelpers.findClass(
                "com.android.server.notification.NotificationManagerService",
                lpparam.classLoader,
            )
            val target = longestEnqueue(nms)
            if (target == null) {
                XposedBridge.log("Notice: enqueueNotificationInternal not found")
                return
            }
            target.isAccessible = true
            val filter = KeywordFilter()
            val inbox = BlockedInbox()
            val callback = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val ctx = nmsContext(param.thisObject)
                        try {
                            inbox.attach(param.thisObject, ctx)
                        } catch (t: Throwable) {
                            XposedBridge.log("Notice: inbox attach failed: $t")
                        }
                        if (!filter.shouldBlock(param.args, ctx)) return
                        param.result = skipResult(param.method)
                        try {
                            inbox.onBlocked(param)
                        } catch (t: Throwable) {
                            XposedBridge.log("Notice: inbox update failed: $t")
                        }
                    } catch (t: Throwable) {
                        XposedBridge.log(t)
                    }
                }
            }
            XposedBridge.hookMethod(target, callback)
            XposedBridge.log(
                "Notice: NMS hooked ${target.parameterCount}-arg enqueueNotificationInternal return=${target.returnType.name}",
            )
        } catch (t: Throwable) {
            XposedBridge.log("Notice: hook NMS failed: $t")
        }
    }

    private fun longestEnqueue(nms: Class<*>): java.lang.reflect.Method? {
        val methods = ArrayList<java.lang.reflect.Method>()
        var current: Class<*>? = nms
        while (current != null && current != Any::class.java) {
            methods += current.declaredMethods.filter { it.name == "enqueueNotificationInternal" }
            current = current.superclass
        }
        return methods.maxByOrNull { it.parameterTypes.size }
    }

    private fun skipResult(method: Any): Any? {
        val type = (method as? java.lang.reflect.Method)?.returnType ?: return null
        if (type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java) {
            return java.lang.Boolean.FALSE
        }
        return null
    }

    private fun nmsContext(service: Any): Context? {
        return try {
            XposedHelpers.callMethod(service, "getContext") as? Context
        } catch (_: Throwable) {
            try {
                XposedHelpers.getObjectField(service, "mContext") as? Context
            } catch (_: Throwable) {
                null
            }
        }
    }

}
