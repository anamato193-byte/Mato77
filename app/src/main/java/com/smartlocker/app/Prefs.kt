package com.smartlocker.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LogEntry(val time: Long, val label: String, val kind: String)

class Prefs private constructor(ctx: Context) {

    private val sp = ctx.applicationContext.getSharedPreferences("locker", Context.MODE_PRIVATE)

    /** القفل الشامل (القفل العكسي): كل التطبيقات مقفلة عدا القائمة البيضاء */
    var masterLock: Boolean
        get() = sp.getBoolean("master", true)
        set(v) { sp.edit().putBoolean("master", v).apply() }

    var whitelist: Set<String>
        get() = HashSet(sp.getStringSet("whitelist", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("whitelist", HashSet(v)).apply() }

    /** عدد الثواني التي يبقى فيها التطبيق مفتوحًا بعد الخروج منه (0 = قفل فوري عند الخروج) */
    var graceSeconds: Int
        get() = sp.getInt("grace", 0)
        set(v) { sp.edit().putInt("grace", v).apply() }

    var relockOnScreenOff: Boolean
        get() = sp.getBoolean("relock_off", true)
        set(v) { sp.edit().putBoolean("relock_off", v).apply() }

    var initialized: Boolean
        get() = sp.getBoolean("init", false)
        set(v) { sp.edit().putBoolean("init", v).apply() }

    fun addLog(label: String, kind: String) {
        val old = try { JSONArray(sp.getString("log", "[]")) } catch (e: Exception) { JSONArray() }
        val out = JSONArray()
        out.put(JSONObject().put("t", System.currentTimeMillis()).put("l", label).put("k", kind))
        for (i in 0 until minOf(old.length(), 49)) out.put(old.get(i))
        sp.edit().putString("log", out.toString()).apply()
    }

    fun logs(): List<LogEntry> {
        val arr = try { JSONArray(sp.getString("log", "[]")) } catch (e: Exception) { JSONArray() }
        val list = ArrayList<LogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(LogEntry(o.optLong("t"), o.optString("l"), o.optString("k")))
        }
        return list
    }

    fun clearLogs() {
        sp.edit().putString("log", "[]").apply()
    }

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs =
            inst ?: synchronized(this) { inst ?: Prefs(ctx).also { inst = it } }
    }
}
