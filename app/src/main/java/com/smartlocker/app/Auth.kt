package com.smartlocker.app

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * يعتمد حصريًا على بصمة الهاتف ورمز/نمط/كلمة مرور قفل الشاشة الحالي للنظام.
 * لا يوجد أي رمز سري خاص بالتطبيق.
 */
object Auth {
    private val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    @Volatile private var ownerUntil = 0L

    fun available(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** جلسة المالك: بعد نجاح التحقق يمكنه التعديل لمدة دقيقتين بدون تكرار البصمة */
    fun ownerActive(): Boolean = System.currentTimeMillis() < ownerUntil
    fun startOwnerSession() { ownerUntil = System.currentTimeMillis() + 120_000L }
    fun endOwnerSession() { ownerUntil = 0L }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onAttemptFailed: () -> Unit = {},
        onError: (Int, CharSequence) -> Unit = { _, _ -> }
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                onAttemptFailed()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode, errString)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
