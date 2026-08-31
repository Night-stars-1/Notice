package moe.notice.filter

import android.app.Application
import android.content.Intent
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import moe.notice.filter.xposed.LogSink

class NoticeApplication : Application(), XposedServiceHelper.OnServiceListener {
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
        // system_server 会在我们未运行时缓冲通知日志；现在请求刷出。
        sendBroadcast(Intent(LogSink.ACTION_FLUSH))
    }

    override fun onServiceBind(service: XposedService) = ModuleStatus.onServiceBound(service)

    override fun onServiceDied(service: XposedService) = ModuleStatus.onServiceDied()
}
