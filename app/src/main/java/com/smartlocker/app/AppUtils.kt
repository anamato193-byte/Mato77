package com.smartlocker.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import android.view.inputmethod.InputMethodManager

object AppUtils {

    /** تطبيقات الإعدادات وحزم التثبيت: تبقى مقفلة دائمًا ولا يمكن إضافتها للقائمة البيضاء (حماية ضد الحذف) */
    val ALWAYS_PROTECTED = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    data class AppInfo(val pkg: String, val label: String, val icon: Drawable)

    fun launchable(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }

    /** الشاشات الرئيسية (Launchers): لا تُقفل أبدًا حتى لا يعلق المستخدم */
    fun launchers(ctx: Context): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** حزم النظام المحايدة: لوحة المفاتيح، شريط الحالة، نوافذ الأذونات... لا تُقفل ولا تُحتسب كخروج */
    fun neutral(ctx: Context): Set<String> {
        val s = mutableSetOf(
            ctx.packageName,
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.intentresolver",
            "com.google.android.gms"
        )
        try {
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.forEach { s.add(it.packageName) }
        } catch (_: Exception) {
        }
        return s
    }

    /** قائمة بيضاء مبدئية آمنة: الاتصال، الرسائل، الكاميرا، واتساب (إن وجدت) */
    fun defaultAllowed(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val result = mutableSetOf<String>()
        val probes = listOf(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:123")),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:123")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        for (p in probes) {
            val name = pm.resolveActivity(p, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            if (name != null && name != "android" && !name.contains("resolver")) result.add(name)
        }
        try {
            pm.getPackageInfo("com.whatsapp", 0)
            result.add("com.whatsapp")
        } catch (_: Exception) {
        }
        return result
    }
}
