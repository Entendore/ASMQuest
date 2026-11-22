package com.example.assemblygame.game

import android.content.Context
import org.json.JSONObject

/** Persists completion, stars, drafts, achievements, settings, stats — all in app-private storage. */
object ProgressStore {

    private const val PREFS = "asm_progress"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- levels ----------

    fun isCompleted(context: Context, id: Int) = prefs(context).getBoolean("done_$id", false)
    fun stars(context: Context, id: Int) = prefs(context).getInt("stars_$id", 0)
    fun usedHint(context: Context, id: Int) = prefs(context).getBoolean("hint_$id", false)
    fun usedDebugger(context: Context, id: Int) = prefs(context).getBoolean("debug_$id", false)

    /** Sandbox (0), dailies (negative ids), level 1 and custom levels (>= 1000) are always open. */
    fun isUnlocked(context: Context, id: Int): Boolean = when {
        id <= 1 -> true
        id >= CustomLevels.ID_BASE -> true
        else -> isCompleted(context, id - 1)
    }

    fun completedCount(context: Context) =
        LevelRepository.levels.count { isCompleted(context, it.id) }

    fun noHintCompletedCount(context: Context) =
        LevelRepository.levels.count { isCompleted(context, it.id) && !usedHint(context, it.id) }

    fun totalStars(context: Context) = LevelRepository.levels.sumOf { stars(context, it.id) }
    fun maxStars(context: Context) = LevelRepository.levels.size * 3

    fun complete(context: Context, id: Int, stars: Int, usedHint: Boolean, usedDebugger: Boolean) {
        val p = prefs(context)
        p.edit()
            .putBoolean("done_$id", true)
            .putInt("stars_$id", maxOf(stars, p.getInt("stars_$id", 0)))
            .putBoolean("hint_$id", p.getBoolean("hint_$id", false) || usedHint)
            .putBoolean("debug_$id", p.getBoolean("debug_$id", false) || usedDebugger)
            .apply()
    }

    // ---------- stats ----------

    fun recordRun(context: Context, id: Int) {
        val p = prefs(context)
        p.edit().putInt("runs_$id", p.getInt("runs_$id", 0) + 1).apply()
    }

    fun totalRuns(context: Context): Int =
        prefs(context).all.filterKeys { it.startsWith("runs_") }
            .values.filterIsInstance<Int>().sum()

    fun recordBest(context: Context, id: Int, steps: Int) {
        val p = prefs(context)
        if (steps < p.getInt("best_$id", Int.MAX_VALUE)) {
            p.edit().putInt("best_$id", steps).apply()
        }
    }

    /** Best (fewest) steps for a level, or -1 if never solved. */
    fun bestSteps(context: Context, id: Int): Int = prefs(context).getInt("best_$id", -1)

    // ---------- daily streak ----------

    fun noteDailyWin(context: Context, dayId: Long) {
        val p = prefs(context)
        val last = p.getLong("dailyLast", -1)
        val streak = when (last) {
            dayId -> p.getInt("dailyStreak", 1)          // already counted today
            dayId - 1 -> p.getInt("dailyStreak", 0) + 1  // consecutive day
            else -> 1                                    // streak broken / first win
        }
        p.edit().putLong("dailyLast", dayId).putInt("dailyStreak", streak).apply()
    }

    fun dailyStreak(context: Context): Int = prefs(context).getInt("dailyStreak", 0)

    // ---------- drafts ----------

    fun saveDraft(context: Context, id: Int, code: String) {
        prefs(context).edit().putString("code_$id", code).apply()
    }

    fun loadDraft(context: Context, id: Int): String? =
        prefs(context).getString("code_$id", null)

    // ---------- achievements ----------

    fun unlockedAchievements(context: Context): Set<String> =
        prefs(context).all.keys.filter { it.startsWith("ach_") }
            .map { it.removePrefix("ach_") }.toSet()

    /** Returns true if this was a NEW unlock. */
    fun unlockAchievement(context: Context, id: String): Boolean {
        if (prefs(context).getBoolean("ach_$id", false)) return false
        prefs(context).edit().putBoolean("ach_$id", true).apply()
        return true
    }

    // ---------- editor preferences ----------

    fun editorFontSize(context: Context): Int = prefs(context).getInt("editorFont", 14)
    fun setEditorFontSize(context: Context, size: Int) =
        prefs(context).edit().putInt("editorFont", size).apply()

    // ---------- settings ----------

    fun soundEnabled(context: Context) = prefs(context).getBoolean("sound", true)
    fun setSound(context: Context, on: Boolean) = prefs(context).edit().putBoolean("sound", on).apply()
    fun vibrationEnabled(context: Context) = prefs(context).getBoolean("vibration", true)
    fun setVibration(context: Context, on: Boolean) = prefs(context).edit().putBoolean("vibration", on).apply()

    // ---------- local backup (file the user picks via SAF) ----------

    fun exportJson(context: Context): String {
        val o = JSONObject()
        o.put("app", "asm-quest")
        o.put("version", 8)
        for ((k, v) in prefs(context).all) {
            when (v) {
                is Int -> o.put(k, v)
                is Long -> o.put(k, v)
                is Boolean -> o.put(k, v)
                is String -> o.put(k, v)
                is Float -> o.put(k, v.toDouble())
            }
        }
        o.put("customLevelsJson", CustomLevels.exportJson(context))
        return o.toString(2)
    }

    /** Overwrites current progress with the backup. Returns the number of restored entries. */
    fun importJson(context: Context, json: String): Int {
        val o = JSONObject(json)
        if (o.optString("app") != "asm-quest")
            throw IllegalArgumentException("Not an ASM Quest backup file")
        val e = prefs(context).edit()
        var count = 0
        for (k in o.keys()) {
            if (k == "app" || k == "version") continue
            when (val v = o.get(k)) {
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Boolean -> e.putBoolean(k, v)
                is String -> if (k != "customLevelsJson") e.putString(k, v)
                is Double -> e.putFloat(k, v.toFloat())
            }
            count++
        }
        if (o.has("customLevelsJson")) {
            CustomLevels.importJson(context, o.optString("customLevelsJson", "[]"))
        }
        e.apply()
        return count
    }

    fun resetAll(context: Context) = prefs(context).edit().clear().apply()
}
