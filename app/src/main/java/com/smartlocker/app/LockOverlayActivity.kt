package com.smartlocker.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity

/** شاشة القفل الفورية التي تظهر فوق أي تطبيق مقفل. زر الرجوع يعيد المستخدم للشاشة الرئيسية. */
class LockOverlayActivity : FragmentActivity() {

    private var targetPkg = ""
    private var label = ""
    private var prompting = false
    private lateinit var titleView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        readTarget(intent)
        setContentView(buildUi())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTarget(intent)
        titleView.text = label
    }

    override fun onResume() {
        super.onResume()
        showPrompt()
    }

    override fun onDestroy() {
        if (showingFor == targetPkg) showingFor = null
        super.onDestroy()
    }

    private fun readTarget(i: Intent) {
        targetPkg = i.getStringExtra(EXTRA_PKG) ?: ""
        showingFor = targetPkg
        label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPkg, 0)).toString()
        } catch (e: Exception) {
            targetPkg
        }
    }

    private fun showPrompt() {
        if (prompting) return
        if (!Auth.available(this)) {
            LockAccessibilityService.instance?.grant(targetPkg)
            finish()
            return
        }
        prompting = true
        Auth.prompt(
            this,
            "🔒 $label",
            getString(R.string.lock_sub),
            onSuccess = {
                prompting = false
                LockAccessibilityService.instance?.grant(targetPkg)
                finish()
            },
            onAttemptFailed = {
                Prefs.get(this).addLog(label, "failed")
            },
            onError = { _, _ ->
                prompting = false
                goHome()
            }
        )
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }

    private fun buildUi(): View {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A172F"))
            setPadding(pad, pad, pad, pad)
        }
        val icon = TextView(this).apply {
            text = "🔒"
            textSize = 72f
            gravity = Gravity.CENTER
        }
        titleView = TextView(this).apply {
            text = label
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = getString(R.string.lock_sub)
            textSize = 14f
            setTextColor(Color.parseColor("#9FB0CC"))
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        }
        val unlock = Button(this).apply {
            text = getString(R.string.unlock)
            setOnClickListener { showPrompt() }
        }
        val home = Button(this).apply {
            text = getString(R.string.go_home)
            setOnClickListener { goHome() }
        }
        root.addView(icon)
        root.addView(titleView)
        root.addView(sub)
        root.addView(unlock)
        root.addView(home)
        return root
    }

    companion object {
        const val EXTRA_PKG = "target_pkg"
        @Volatile var showingFor: String? = null
    }
}
