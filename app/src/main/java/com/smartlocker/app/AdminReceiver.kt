package com.smartlocker.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** صلاحية مسؤول الجهاز: تمنع نظام أندرويد من حذف التطبيق طالما أنها نشطة */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)
}
