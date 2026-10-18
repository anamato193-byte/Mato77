package com.smartlocker.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * يراقب التطبيق المفتوح حاليًا:
 *  - ضمن القائمة البيضاء  -> يفتح فورًا
 *  - غير مسموح (أو شاشة الإعدادات/حذف التطبيق) -> يظهر LockOverlayActivity فورًا
 */
class LockAccessibilityService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val authorized = HashMap<String, Long>()
    private var current: String? = null
    private var launchers: Set<String> = emptySet()
    private var neutral: Set<String> = emptySet()
    private var refreshedAt = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (prefs.relockOnScreenOff) {
                authorized.clear()
                current = null
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.get(this)
        instance = this
        ContextCompat.registerReceiver(
            this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) { }
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    /** يُستدعى بعد نجاح التحقق: التطبيق يبقى مفتوحًا طالما المستخدم بداخله */
    fun grant(pkg: String) {
        authorized[pkg] = Long.MAX_VALUE
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()

        if (now - refreshedAt > 30_000L) {
            launchers = AppUtils.launchers(this)
            neutral = AppUtils.neutral(this)
            refreshedAt = now
        }
        if (pkg == packageName || pkg in neutral) return

        // تتبّع الخروج من التطبيق لحساب مهلة إعادة القفل
        if (pkg != current) {
            current?.let { prev ->
                if (authorized[prev] == Long.MAX_VALUE) {
                    authorized[prev] = now + prefs.graceSeconds * 1000L
                }
            }
            current = pkg
        }

        if (!prefs.masterLock) return
        if (pkg in launchers) return
        if (pkg !in AppUtils.ALWAYS_PROTECTED && pkg in prefs.whitelist) return
        // لو لا يوجد قفل شاشة/بصمة على الهاتف لا نقفل حتى لا يُحبس المستخدم خارج هاتفه
        if (!Auth.available(this)) return

        val exp = authorized[pkg]
        if (exp != null && exp > now) {
            authorized[pkg] = Long.MAX_VALUE
            return
        }
        if (LockOverlayActivity.showingFor == pkg) return

        val i = Intent(this, LockOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(LockOverlayActivity.EXTRA_PKG, pkg)
        startActivity(i)
    }

    companion object {
        @Volatile var instance: LockAccessibilityService? = null
    }
}
