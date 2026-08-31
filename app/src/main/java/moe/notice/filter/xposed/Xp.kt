package moe.notice.filter.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/** 持有当前进程的框架接口，并提供简单的日志与反射辅助方法。 */
internal object Xp {
    private const val TAG = "Notice"

    @Volatile
    var api: XposedInterface? = null

    /** 写入框架日志；[mirror] 为 true 时同时镜像到应用的「运行日志」页面。 */
    fun log(msg: String, mirror: Boolean = true) {
        val a = api
        if (a != null) a.log(Log.INFO, TAG, msg) else Log.i(TAG, msg)
        if (mirror) DebugLog.append(Log.INFO, msg, null)
    }

    fun log(msg: String, t: Throwable, mirror: Boolean = true) {
        val a = api
        if (a != null) a.log(Log.ERROR, TAG, msg, t) else Log.e(TAG, msg, t)
        if (mirror) DebugLog.append(Log.ERROR, msg, t)
    }

    /** 调用原始实现，绕过 [method] 上的所有 hook。 */
    fun invokeOrigin(method: Method, thisObject: Any?, args: Array<Any?>): Any? {
        val a = api ?: throw IllegalStateException("framework not attached")
        return a.getInvoker(method)
            .setType(XposedInterface.Invoker.Type.ORIGIN)
            .invoke(thisObject, *args)
    }

    fun callMethod(target: Any, name: String, vararg args: Any?): Any? {
        val method = findMethod(target.javaClass, name, args)
            ?: throw NoSuchMethodException("${target.javaClass.name}.$name/${args.size}")
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    fun getField(target: Any, name: String): Any? {
        val field = findField(target.javaClass, name)
            ?: throw NoSuchFieldException("${target.javaClass.name}.$name")
        field.isAccessible = true
        return field.get(target)
    }

    private fun findMethod(cls: Class<*>, name: String, args: Array<out Any?>): Method? {
        var current: Class<*>? = cls
        while (current != null) {
            val match = current.declaredMethods.firstOrNull { m ->
                m.name == name && m.parameterCount == args.size &&
                    m.parameterTypes.withIndex().all { (i, type) -> accepts(type, args[i]) }
            }
            if (match != null) return match
            current = current.superclass
        }
        return null
    }

    private fun findField(cls: Class<*>, name: String): Field? {
        var current: Class<*>? = cls
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun accepts(type: Class<*>, value: Any?): Boolean {
        if (value == null) return !type.isPrimitive
        if (type.isInstance(value)) return true
        val boxed = when (type) {
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            else -> return false
        }
        return boxed.isInstance(value)
    }
}
