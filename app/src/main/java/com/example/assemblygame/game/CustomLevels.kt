package com.example.assemblygame.game

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Player-made levels, stored only on this device.
 * Shareable as a short "ASMQ1…" text code (clipboard only — the app never transmits anything).
 */
object CustomLevels {

    const val ID_BASE = 1000
    private const val CODE_PREFIX = "ASMQ1."
    private const val PREFS = "custom_levels"

    data class Entry(
        val id: Int,
        val title: String,
        val task: String,
        val hint: String,
        val starterCode: String,
        val parSteps: Int,
        val banned: List<String>,
        val tests: List<Pair<List<Int>, List<String>>>,
        val canvas: List<String>? = null    // 16 lines of 16 chars: '.', '#', '+'
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- storage ----------

    fun all(context: Context): List<Entry> {
        val out = mutableListOf<Entry>()
        try {
            val arr = JSONArray(prefs(context).getString("levels", "[]"))
            for (i in 0 until arr.length()) fromJson(arr.optJSONObject(i))?.let { out.add(it) }
        } catch (_: Exception) { /* corrupted store — start empty */ }
        return out
    }

    fun get(context: Context, id: Int): Entry? = all(context).firstOrNull { it.id == id }
    fun nextAfter(context: Context, id: Int): Entry? = all(context).firstOrNull { it.id > id }
    fun newId(context: Context): Int =
        (all(context).maxOfOrNull { it.id } ?: (ID_BASE - 1)) + 1

    fun save(context: Context, entry: Entry) {
        val list = all(context).filterNot { it.id == entry.id } + entry
        write(context, list.sortedBy { it.id })
    }

    fun delete(context: Context, id: Int) = write(context, all(context).filterNot { it.id == id })
    fun deleteAll(context: Context) = prefs(context).edit().clear().apply()

    private fun entryJson(e: Entry): JSONObject {
        val cases = JSONArray()
        for ((input, expected) in e.tests) {
            cases.put(JSONObject().put("i", JSONArray(input)).put("o", JSONArray(expected)))
        }
        val o = JSONObject()
            .put("t", e.title).put("k", e.task).put("h", e.hint)
            .put("s", e.starterCode).put("p", e.parSteps)
            .put("b", JSONArray(e.banned)).put("c", cases)
        e.canvas?.let { o.put("cv", JSONArray(it)) }
        return o
    }

    private fun write(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) arr.put(entryJson(e).put("id", e.id))
        prefs(context).edit().putString("levels", arr.toString()).apply()
    }

    /** Bridges into the game's Level type so GameActivity needs no special cases. */
    fun asLevel(entry: Entry): Level = Level(
        id = entry.id,
        title = entry.title,
        task = entry.task,
        hint = entry.hint.ifEmpty { "(no hint)" },
        starterCode = entry.starterCode,
        parSteps = entry.parSteps,
        bannedSnippets = entry.banned,
        testCases = entry.tests.map { TestCase(it.first, it.second) },
        initialCanvas = null,
        expectedCanvas = entry.canvas
    )

    // ---------- share codes ----------

    fun encode(entry: Entry): String =
        CODE_PREFIX + Base64.encodeToString(
            entryJson(entry).toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    fun decode(code: String): Entry? {
        val trimmed = code.trim()
        if (!trimmed.startsWith(CODE_PREFIX)) return null
        return try {
            val json = String(
                Base64.decode(
                    trimmed.removePrefix(CODE_PREFIX),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                ),
                Charsets.UTF_8
            )
            fromJson(JSONObject(json))
        } catch (_: Exception) { null }
    }

    /** Parses + validates. Used for both storage and imported codes, so nothing hostile can sneak in. */
    private fun fromJson(o: JSONObject?): Entry? {
        o ?: return null
        val title = o.optString("t").trim()
        val task = o.optString("k").trim()
        if (title.isEmpty() || task.isEmpty()) return null
        if (title.length > 40 || task.length > 500) return null
        val hint = o.optString("h").take(300)
        val starter = o.optString("s").take(1500)
        val par = o.optInt("p", 20).coerceIn(1, 100_000)

        val banned = mutableListOf<String>()
        val bArr = o.optJSONArray("b") ?: JSONArray()
        for (i in 0 until minOf(bArr.length(), 5)) {
            val s = bArr.optString(i).trim().take(12)
            if (s.isNotEmpty()) banned.add(s)
        }

        val cArr = o.optJSONArray("c") ?: return null
        if (cArr.length() !in 1..10) return null
        val tests = mutableListOf<Pair<List<Int>, List<String>>>()
        for (i in 0 until cArr.length()) {
            val c = cArr.optJSONObject(i) ?: return null
            val iArr = c.optJSONArray("i") ?: return null
            if (iArr.length() > 8) return null
            val input = mutableListOf<Int>()
            for (j in 0 until iArr.length()) {
                val v = iArr.optLong(j, Long.MIN_VALUE)
                if (v == Long.MIN_VALUE || v < -1_000_000 || v > 1_000_000) return null
                input.add(v.toInt())
            }
            val oArr = c.optJSONArray("o") ?: return null
            if (oArr.length() !in 1..100) return null
            val expected = mutableListOf<String>()
            for (j in 0 until oArr.length()) {
                val line = oArr.optString(j).take(60)
                if (line.isEmpty()) return null
                expected.add(line)
            }
            tests.add(input to expected)
        }

        // optional pixel-art target
        var canvas: List<String>? = null
        if (o.has("cv")) {
            val cv = o.optJSONArray("cv") ?: return null
            if (cv.length() != AssemblyVM.CANVAS_SIZE) return null
            val rows = mutableListOf<String>()
            for (i in 0 until cv.length()) {
                val row = cv.optString(i)
                if (row.length != AssemblyVM.CANVAS_SIZE || row.any { it !in ".#+" }) return null
                rows.add(row)
            }
            canvas = rows
        }

        return Entry(o.optInt("id", ID_BASE), title, task, hint, starter, par, banned, tests, canvas)
    }

    // ---------- backup integration ----------

    fun exportJson(context: Context): String = prefs(context).getString("levels", "[]") ?: "[]"
    fun importJson(context: Context, json: String) {
        prefs(context).edit().putString("levels", json).apply()
    }
}
