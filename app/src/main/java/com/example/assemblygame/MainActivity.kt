package com.example.assemblygame

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.assemblygame.game.Achievements
import com.example.assemblygame.game.CustomLevels
import com.example.assemblygame.game.DailyChallenge
import com.example.assemblygame.game.Level
import com.example.assemblygame.game.LevelRepository
import com.example.assemblygame.game.ProgressStore
import com.example.assemblygame.ui.LevelAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: LevelAdapter
    private lateinit var progressText: TextView

    // Local backup via the system file picker — no storage permission, no network.
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportProgress(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importProgress(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressText = findViewById(R.id.progressText)

        adapter = LevelAdapter(
            onClick = { level -> openGame(level.id) },
            onLongClick = { level -> onLevelLongPress(level) }
        )
        findViewById<RecyclerView>(R.id.levelRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<MaterialButton>(R.id.btnSandbox).setOnClickListener { openGame(0) }
        findViewById<MaterialButton>(R.id.btnDaily).setOnClickListener { showDaily() }
        findViewById<MaterialButton>(R.id.btnEditor).setOnClickListener {
            startActivity(Intent(this, LevelEditorActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnImport).setOnClickListener { showImportDialog() }
        findViewById<MaterialButton>(R.id.btnAchievements).setOnClickListener { showAchievements() }
        findViewById<MaterialButton>(R.id.btnStats).setOnClickListener { showStats() }
        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener { showSettings() }

        // Long-press the progress line to reset everything.
        progressText.setOnLongClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset progress")
                .setMessage("Delete all stars, achievements, settings, saved code and custom levels?")
                .setPositiveButton("Reset") { _, _ ->
                    ProgressStore.resetAll(this)
                    CustomLevels.deleteAll(this)
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------- level list ----------

    private fun buildRows(): List<LevelAdapter.Row> {
        val rows = mutableListOf<LevelAdapter.Row>()
        rows += LevelAdapter.Row.Header("CAMPAIGN")
        LevelRepository.levels.filter { it.id in 1..22 }.forEach {
            rows += LevelAdapter.Row.Game(it, ProgressStore.isUnlocked(this, it.id),
                ProgressStore.stars(this, it.id))
        }
        rows += LevelAdapter.Row.Header("GRAPHICS, TEXT, DATA & MELODIES")
        LevelRepository.levels.filter { it.id >= 23 }.forEach {
            rows += LevelAdapter.Row.Game(it, ProgressStore.isUnlocked(this, it.id),
                ProgressStore.stars(this, it.id))
        }
        val customs = CustomLevels.all(this)
        if (customs.isNotEmpty()) {
            rows += LevelAdapter.Row.Header("CUSTOM LEVELS")
            customs.forEach {
                rows += LevelAdapter.Row.Game(CustomLevels.asLevel(it), true,
                    ProgressStore.stars(this, it.id))
            }
        }
        return rows
    }

    private fun refresh() {
        adapter.submit(buildRows())
        val stars = ProgressStore.totalStars(this)
        val customs = CustomLevels.all(this).size
        progressText.text =
            "Rank: ${Achievements.rankFor(stars)}\n" +
            "${ProgressStore.completedCount(this)} / ${LevelRepository.levels.size} levels  •  " +
            "$stars ★ / ${ProgressStore.maxStars(this)}  (long-press to reset)" +
            if (customs > 0) "\n$customs custom level(s) installed" else ""
    }

    private fun openGame(id: Int) {
        startActivity(
            Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_LEVEL_ID, id)
        )
    }

    private fun onLevelLongPress(level: Level): Boolean {
        if (level.id < CustomLevels.ID_BASE) return false
        MaterialAlertDialogBuilder(this)
            .setTitle(level.title)
            .setMessage(level.task)
            .setPositiveButton("Play") { _, _ -> openGame(level.id) }
            .setNeutralButton("Edit") { _, _ ->
                startActivity(
                    Intent(this, LevelEditorActivity::class.java)
                        .putExtra(LevelEditorActivity.EXTRA_CUSTOM_ID, level.id)
                )
            }
            .setNegativeButton("Delete") { _, _ ->
                CustomLevels.delete(this, level.id)
                refresh()
            }
            .show()
        return true
    }

    // ---------- daily ----------

    private fun showDaily() {
        val day = DailyChallenge.todayId()
        val lvl = DailyChallenge.levelForDay(day)
        val stars = ProgressStore.stars(this, lvl.id)
        MaterialAlertDialogBuilder(this)
            .setTitle("Daily Challenge — ${DailyChallenge.dateLabel(day)}")
            .setMessage(
                lvl.task +
                (if (stars > 0) "\n\nAlready solved today: ${"★".repeat(stars)}" else "") +
                "\n\n🔥 Streak: ${ProgressStore.dailyStreak(this)} day(s)"
            )
            .setPositiveButton("Play") { _, _ -> openGame(lvl.id) }
            .setNegativeButton("Later", null)
            .show()
    }

    // ---------- share codes (clipboard only) ----------

    private fun showImportDialog() {
        val input = EditText(this).apply { hint = "ASMQ1…"; setSingleLine(true) }
        MaterialAlertDialogBuilder(this)
            .setTitle("Import a shared level")
            .setMessage("Paste a level code someone shared with you. It is added to the bottom of your level list.")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val entry = CustomLevels.decode(input.text.toString())
                if (entry == null) {
                    toast("That code is not a valid ASM Quest level")
                } else {
                    val saved = entry.copy(id = CustomLevels.newId(this))
                    CustomLevels.save(this, saved)
                    toast("Imported \"${saved.title}\"")
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- info dialogs ----------

    private fun showAchievements() {
        val unlocked = ProgressStore.unlockedAchievements(this)
        val text = Achievements.ALL.joinToString("\n\n") { a ->
            "${if (a.id in unlocked) "★" else "🔒"} ${a.title}\n${a.desc}"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Achievements  ${unlocked.size}/${Achievements.ALL.size}")
            .setMessage(text)
            .setPositiveButton("Nice", null)
            .show()
    }

    private fun showStats() {
        val stars = ProgressStore.totalStars(this)
        val sb = StringBuilder()
        sb.append("Rank: ${Achievements.rankFor(stars)}\n")
        sb.append("Levels: ${ProgressStore.completedCount(this)} / ${LevelRepository.levels.size}\n")
        sb.append("Stars: $stars / ${ProgressStore.maxStars(this)}\n")
        sb.append("Achievements: ${ProgressStore.unlockedAchievements(this).size} / ${Achievements.ALL.size}\n")
        sb.append("Total runs: ${ProgressStore.totalRuns(this)}\n")
        sb.append("Daily streak: ${ProgressStore.dailyStreak(this)} 🔥\n")
        sb.append("\n── BEST STEPS ──────────────\n")
        val solved = LevelRepository.levels.filter { ProgressStore.isCompleted(this, it.id) }
        if (solved.isEmpty()) sb.append("(solve some levels first)")
        for (lvl in solved) {
            val best = ProgressStore.bestSteps(this, lvl.id)
            val parMark = if (best in 1..lvl.parSteps) " 3★" else ""
            sb.append("L${lvl.id}".padEnd(5))
                .append(lvl.title.take(20).padEnd(20))
                .append(if (best >= 0) "$best$parMark" else "?")
                .append('\n')
        }
        val tv = TextView(this).apply {
            text = sb.toString().trimEnd()
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(48, 24, 48, 24)
            setTextColor(0xFFE6EDF3.toInt())
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(this)
            .setTitle("📊 Your stats")
            .setView(scroll)
            .setPositiveButton("Nice", null)
            .show()
    }

    private fun showSettings() {
        val options = arrayOf("Sound effects", "Vibration")
        val checked = booleanArrayOf(
            ProgressStore.soundEnabled(this),
            ProgressStore.vibrationEnabled(this)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setMultiChoiceItems(options, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                ProgressStore.setSound(this, checked[0])
                ProgressStore.setVibration(this, checked[1])
            }
            .setNeutralButton("Backup…") { _, _ -> showBackupDialog() }
            .show()
    }

    private fun showBackupDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Backup / Restore")
            .setMessage(
                "Your progress lives only on this device. " +
                "Save it to a file you choose, or restore from a previous backup."
            )
            .setPositiveButton("Export to file") { _, _ ->
                exportLauncher.launch("asm-quest-backup.json")
            }
            .setNeutralButton("Import from file") { _, _ ->
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportProgress(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use {
                it.write(ProgressStore.exportJson(this).toByteArray())
            }
            toast("Backup saved")
        } catch (e: Exception) {
            toast("Backup failed: ${e.message}")
        }
    }

    private fun importProgress(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()
                ?.use { it.readText() } ?: ""
            val n = ProgressStore.importJson(this, text)
            toast("Restored $n entries")
            refresh()
        } catch (e: Exception) {
            toast("Restore failed: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
