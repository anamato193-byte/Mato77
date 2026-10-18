package com.smartlocker.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0A172F)
private val Surf = Color(0xFF12213F)
private val Emerald = Color(0xFF10B981)
private val Muted = Color(0xFF9FB0CC)
private val Danger = Color(0xFFEF4444)

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap)

class MainActivity : FragmentActivity() {

    private lateinit var prefs: Prefs

    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var whitelist by mutableStateOf<Set<String>>(emptySet())
    private var master by mutableStateOf(true)
    private var grace by mutableIntStateOf(0)
    private var relockOff by mutableStateOf(true)
    private var logs by mutableStateOf<List<LogEntry>>(emptyList())
    private var adminActive by mutableStateOf(false)
    private var a11yOn by mutableStateOf(false)
    private var lockAvail by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)

        lifecycleScope.launch(Dispatchers.Default) {
            val list = AppUtils.launchable(this@MainActivity).map {
                AppEntry(it.pkg, it.label, it.icon.toBitmap(96, 96).asImageBitmap())
            }
            if (!prefs.initialized) {
                prefs.whitelist = AppUtils.defaultAllowed(this@MainActivity)
                prefs.initialized = true
            }
            withContext(Dispatchers.Main) {
                apps = list
                refresh()
            }
        }

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Emerald,
                        background = Bg,
                        surface = Surf,
                        onSurface = Color.White,
                        onBackground = Color.White
                    )
                ) {
                    Root()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ------------------------------------------------------------------ منطق

    private fun refresh() {
        whitelist = prefs.whitelist
        master = prefs.masterLock
        grace = prefs.graceSeconds
        relockOff = prefs.relockOnScreenOff
        logs = prefs.logs()
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminActive = dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        a11yOn = isA11yOn()
        lockAvail = Auth.available(this)
    }

    private fun isA11yOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, LockAccessibilityService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }

    /** أي إجراء حساس يتطلب بصمة الهاتف أو رمز قفله الحالي */
    private fun requireOwner(action: () -> Unit) {
        if (Auth.ownerActive() || !Auth.available(this)) {
            action()
            return
        }
        Auth.prompt(
            this,
            getString(R.string.auth_title),
            getString(R.string.auth_owner_sub),
            onSuccess = {
                Auth.startOwnerSession()
                action()
            }
        )
    }

    private fun toggleApp(pkg: String, allow: Boolean) {
        whitelist = if (allow) whitelist + pkg else whitelist - pkg
        prefs.whitelist = whitelist
    }

    private fun launchApp(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i)
    }

    private fun requestAdmin() {
        val cn = ComponentName(this, AdminReceiver::class.java)
        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explain))
        startActivity(i)
    }

    private fun removeAdmin() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.removeActiveAdmin(ComponentName(this, AdminReceiver::class.java))
        refresh()
    }

    // ------------------------------------------------------------------ واجهة

    @Composable
    private fun Root() {
        var tab by remember { mutableIntStateOf(0) }
        val tabs = listOf(
            "الرئيسية" to Icons.Filled.Home,
            "تحديد البرامج" to Icons.Filled.List,
            "الشاشة" to Icons.Filled.Menu,
            "الحماية" to Icons.Filled.Lock
        )
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Surf) {
                    tabs.forEachIndexed { i, (name, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icon, contentDescription = name) },
                            label = { Text(name, fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeTab(onGoProtect = { tab = 3 })
                    1 -> AppsTab()
                    2 -> LauncherTab()
                    else -> ProtectTab()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeTab(onGoProtect: () -> Unit) {
        var sheet by remember { mutableStateOf(false) }
        val allowed = apps.filter { it.pkg in whitelist }
        val setupOk = a11yOn && adminActive && lockAvail

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حارس التطبيقات الذكي", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatChip("مسموحة", allowed.size.toString(), Emerald, Modifier.weight(1f))
                        StatChip("مقفلة", (apps.size - allowed.size).coerceAtLeast(0).toString(), Danger, Modifier.weight(1f))
                        StatChip("منع الحذف", if (adminActive) "مفعّل" else "متوقف", if (adminActive) Emerald else Danger, Modifier.weight(1f))
                    }
                    if (!setupOk) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1D1D)),
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onGoProtect)
                        ) {
                            Text(
                                "⚠️ الحماية غير مكتملة. اضغط هنا لإكمال التفعيل (خدمة المراقبة + صلاحية منع الحذف + قفل الشاشة).",
                                color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    CenterButton(active = master) { sheet = true }
                    Text("اضغط الزر للوصول السريع أو لإيقاف/تشغيل القفل", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("وصول سريع للبرامج المسموحة", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(allowed, key = { it.pkg }) { app ->
                AppTile(app, "✅") { launchApp(app.pkg) }
            }
        }

        if (sheet) {
            ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = Surf) {
                Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("القفل الشامل", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (master) "كل التطبيقات مقفلة عدا المسموحة" else "القفل متوقف مؤقتًا",
                                color = Muted, fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = master,
                            colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                            onCheckedChange = { v ->
                                requireOwner {
                                    prefs.masterLock = v
                                    master = v
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("افتح بضغطة واحدة بدون بصمة", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        if (allowed.isEmpty()) {
                            Text("لا توجد تطبيقات مسموحة بعد. أضفها من تبويب «تحديد البرامج».", color = Muted, fontSize = 13.sp)
                        }
                        allowed.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { app ->
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        AppTile(app, null) {
                                            sheet = false
                                            launchApp(app.pkg)
                                        }
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppsTab() {
        var q by remember { mutableStateOf("") }
        var filter by remember { mutableIntStateOf(0) }
        val shown = apps.filter { a ->
            a.label.contains(q, ignoreCase = true) && when (filter) {
                1 -> a.pkg in whitelist
                2 -> a.pkg !in whitelist
                else -> true
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("تحديد البرامج", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("المفتاح مفعّل = مسموح بفتحه بدون بصمة. غير ذلك = مقفل.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث عن تطبيق") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf("الكل", "المسموحة فقط", "المقفلة").forEachIndexed { i, s ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(s) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.pkg }) { a ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Image(bitmap = a.icon, contentDescription = null, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(
                            a.label, color = Color.White, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (a.pkg in AppUtils.ALWAYS_PROTECTED) {
                            Text("🔒 محمي دائمًا", color = Muted, fontSize = 12.sp)
                        } else {
                            Switch(
                                checked = a.pkg in whitelist,
                                colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                                onCheckedChange = { v -> requireOwner { toggleApp(a.pkg, v) } }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LauncherTab() {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("شاشة الهاتف (المشغل السريع)", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("✅ يفتح فورًا    🔒 يطلب بصمة الهاتف", color = Muted, fontSize = 12.sp)
                }
            }
            items(apps, key = { it.pkg }) { app ->
                val ok = app.pkg in whitelist && app.pkg !in AppUtils.ALWAYS_PROTECTED
                AppTile(app, if (ok) "✅" else "🔒") { launchApp(app.pkg) }
            }
        }
    }

    @Composable
    private fun ProtectTab() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("البصمة وعدم الحذف", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            if (!lockAvail) {
                Panel("⚠️ لا يوجد قفل شاشة أو بصمة") {
                    Text(
                        "التطبيق يعتمد على بصمة الهاتف أو رمز قفل الشاشة. فعّل أحدهما من إعدادات الأمان، وإلا لن يعمل القفل.",
                        color = Muted, fontSize = 13.sp
                    )
                    GreenButton("فتح إعدادات الأمان") { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                }
            }

            Panel("1) صلاحية مسؤول الجهاز (منع الحذف)") {
                StatusLine(if (adminActive) "مفعّلة" else "غير مفعّلة", adminActive)
                Text("تمنع حذف التطبيق من النظام طالما أنها نشطة.", color = Muted, fontSize = 12.sp)
                if (!adminActive) {
                    GreenButton("تفعيل الصلاحية") { requestAdmin() }
                } else {
                    OutlinedButton(onClick = { requireOwner { removeAdmin() } }) { Text("إلغاء التفعيل (يتطلب بصمة)") }
                }
            }

            Panel("2) خدمة المراقبة (إمكانية الوصول)") {
                StatusLine(if (a11yOn) "تعمل" else "متوقفة", a11yOn)
                Text(
                    "تراقب التطبيقات المفتوحة وتقفل غير المسموح بها، وتحمي شاشات الإعدادات والحذف.",
                    color = Muted, fontSize = 12.sp
                )
                if (!a11yOn) {
                    Text(
                        "ملاحظة (أندرويد 13 فأحدث): إذا ظهرت الخدمة رمادية، افتح «معلومات التطبيق» ثم القائمة ⋮ واختر «السماح بالإعدادات المقيّدة»، ثم ارجع وفعّل الخدمة.",
                        color = Color(0xFFFFD27A), fontSize = 12.sp
                    )
                    GreenButton("فتح إعدادات إمكانية الوصول") {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    OutlinedButton(onClick = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        )
                    }) { Text("فتح معلومات التطبيق") }
                }
            }

            Panel("3) القفل الشامل") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (master) "مفعّل: كل التطبيقات مقفلة عدا المسموحة" else "متوقف مؤقتًا",
                        color = Color.White, modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = master,
                        colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                        onCheckedChange = { v -> requireOwner { prefs.masterLock = v; master = v } }
                    )
                }
            }

            Panel("4) مهلة إعادة القفل بعد الخروج من التطبيق") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("فورًا" to 0, "30 ثانية" to 30, "دقيقة" to 60, "5 دقائق" to 300).forEach { (name, sec) ->
                        FilterChip(
                            selected = grace == sec,
                            onClick = { requireOwner { prefs.graceSeconds = sec; grace = sec } },
                            label = { Text(name, fontSize = 12.sp) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("إعادة القفل عند إطفاء الشاشة", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = relockOff,
                        colors = SwitchDefaults.colors(checkedTrackColor = Emerald),
                        onCheckedChange = { v -> requireOwner { prefs.relockOnScreenOff = v; relockOff = v } }
                    )
                }
            }

            Panel("5) سجل المحاولات الفاشلة") {
                if (logs.isEmpty()) {
                    Text("لا توجد محاولات فاشلة 👍", color = Muted, fontSize = 13.sp)
                } else {
                    val fmt = SimpleDateFormat("dd/MM  HH:mm", Locale.getDefault())
                    logs.take(15).forEach {
                        Text("• ${fmt.format(Date(it.time))} — محاولة فاشلة لفتح ${it.label}", color = Color.White, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { requireOwner { prefs.clearLogs(); logs = emptyList() } }) { Text("مسح السجل") }
                }
            }

            Text(
                "تنبيه: حماية عدم الحذف تمنع الحذف العادي. لا يمكنها منع الحذف عبر وضع الأمان أو أدوات المطوّرين (ADB) أو ضبط المصنع.",
                color = Muted, fontSize = 11.sp
            )
        }
    }
}

// ---------------------------------------------------------------------- مكونات عامة

@Composable
private fun CenterButton(active: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val glow by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Reverse),
        label = "glow"
    )
    val color = if (active) Emerald else Danger
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Box(
            Modifier.size(220.dp).background(
                Brush.radialGradient(listOf(color.copy(alpha = glow), Color.Transparent)), CircleShape
            )
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.6f))))
                .clickable(onClick = onClick)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                Text(if (active) "القفل مفعّل" else "القفل متوقف", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatChip(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Surf).padding(vertical = 10.dp)
    ) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(title, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AppTile(app: AppEntry, badge: String?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    ) {
        Box {
            Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(52.dp))
            if (badge != null) {
                Text(badge, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Text(
            app.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Surf), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            content()
        }
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Text((if (ok) "✅ " else "❌ ") + text, color = if (ok) Emerald else Danger, fontWeight = FontWeight.Bold)
}

@Composable
private fun GreenButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Emerald)) { Text(text) }
}
