package moe.notice.filter.xposed

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/** 现代（libxposed API）入口；在 META-INF/xposed/java_init.list 中声明。 */
class NoticeModule : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        Xp.api = this
        Xp.log(
            "loaded in ${param.processName}: $frameworkName $frameworkVersion ($frameworkVersionCode) api=$apiVersion",
        )
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        Xp.api = this
        hookSystemServer(param.classLoader)
    }

    private fun hookSystemServer(classLoader: ClassLoader) {
        try {
            val nms = Class.forName(
                "com.android.server.notification.NotificationManagerService",
                false,
                classLoader,
            )
            val target = longestEnqueue(nms)
            if (target == null) {
                Xp.log("enqueueNotificationInternal not found")
                return
            }
            val filter = KeywordFilter().also { it.attach(this) }
            val inbox = BlockedInbox()
            hook(target).intercept { chain ->
                val service = chain.thisObject
                val args = chain.args.toTypedArray()
                val ctx = nmsContext(service)
                try {
                    inbox.attach(service, ctx)
                } catch (t: Throwable) {
                    Xp.log("inbox attach failed", t)
                }
                val block = try {
                    filter.shouldBlock(args, ctx)
                } catch (t: Throwable) {
                    Xp.log("filter failed", t)
                    false
                }
                if (!block) return@intercept chain.proceed()
                try {
                    inbox.onBlocked(chain.executable as Method, service, args)
                } catch (t: Throwable) {
                    Xp.log("inbox update failed", t)
                }
                skipResult(target)
            }
            Xp.log(
                "NMS hooked ${target.parameterCount}-arg enqueueNotificationInternal return=${target.returnType.name}",
            )
        } catch (t: Throwable) {
            Xp.log("hook NMS failed", t)
        }
    }

    private fun longestEnqueue(nms: Class<*>): Method? {
        val methods = ArrayList<Method>()
        var current: Class<*>? = nms
        while (current != null && current != Any::class.java) {
            methods += current.declaredMethods.filter { it.name == "enqueueNotificationInternal" }
            current = current.superclass
        }
        return methods.maxByOrNull { it.parameterTypes.size }
    }

    private fun skipResult(method: Method): Any? {
        val type = method.returnType
        if (type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java) {
            return java.lang.Boolean.FALSE
        }
        return null
    }

    private fun nmsContext(service: Any): Context? {
        return try {
            Xp.callMethod(service, "getContext") as? Context
        } catch (_: Throwable) {
            try {
                Xp.getField(service, "mContext") as? Context
            } catch (_: Throwable) {
                null
            }
        }
    }
}
