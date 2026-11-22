package com.example.assemblygame

import android.content.Intent
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.assemblygame.audio.BeepPlayer
import com.example.assemblygame.game.Achievements
import com.example.assemblygame.game.AssemblyVM
import com.example.assemblygame.game.Beep
import com.example.assemblygame.game.CustomLevels
import com.example.assemblygame.game.DailyChallenge
import com.example.assemblygame.game.Level
import com.example.assemblygame.game.LevelRepository
import com.example.assemblygame.game.ProgressStore
import com.example.assemblygame.ui.PixelGridView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

sealed class RunResult {
    data class Error(val vm: AssemblyVM, val message: String, val line: Int? = null) : RunResult()
    data class Success(val vm: AssemblyVM, val passed: Boolean, val judgeMessage: String) : RunResult()
}

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LEVEL_ID = "extra_level_id"
        private val TOKEN_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*:?|-?[0-9]+")
        private val GRAPHICS_REGEX = Regex("(?i)\\b(PLOT|PEN|PIX|CLR|LINE|RECT|FILL)\\b")
        private const val PLAY_TICK_MS = 50L
        private val PLAY_STEPS_PER_TICK = intArrayOf(1, 5, 25)
        private val PLAY_SPEED_LABELS = arrayOf("1x slow", "5x", "25x fast")
        private val FONT_SIZES = intArrayOf(12, 14, 16, 18)
        private const val MAX_FRAMES = 400

        private val SNIPPETS = listOf(
            "MOV" to "MOV A, ",
            "ADD" to "ADD A, ",
            "CMP" to "CMP A, 0",
            "JMP" to "JMP label",
            "JE" to "JE label",
            "JLE" to "JLE label",
            "IN" to "IN A",
            "OUT" to "OUT A",
            "OUTC" to "OUTC 65",
            "BEEP" to "BEEP 440, 300",
            "PLOT" to "PLOT 8, 8",
            "LINE" to "LINE 0, 0, 15, 0",
            "RECT" to "RECT 2, 2, 13, 13",
            "FILL" to "FILL 0, 0, 15, 15",
            "PEN" to "PEN 1",
            "PIX" to "PIX A, 0, 0",
            "CLR" to "CLR",
            "LOOP" to "MOV A, 0\nloop:\n  \n  INC A\n  CMP A, 5\n  JL loop"
        )

        private val DEMOS = listOf(
            "Checkerboard" to
                "MOV C, 0\nrow:\nMOV A, 0\ncol:\nMOV B, A\nADD B, C\nAND B, 1\nCMP B, 1\nJE odd\nPEN 1\nJMP put\nodd:\nPEN 2\nput:\nPLOT A, C\nINC A\nCMP A, 16\nJL col\nINC C\nCMP C, 16\nJL row\nHLT",
            "Concentric rings" to
                "MOV A, 0\nloop:\nMOV B, A\nAND B, 1\nCMP B, 1\nJE odd\nPEN 1\nJMP d\nodd:\nPEN 2\nd:\nMOV D, 15\nSUB D, A\nRECT A, A, D, D\nINC A\nCMP A, 8\nJL loop\nHLT",
            "Zigzag mountains" to
                "CLR\nPEN 1\nLINE 0, 15, 3, 12\nLINE 3, 12, 6, 15\nLINE 6, 15, 9, 12\nLINE 9, 12, 12, 15\nLINE 12, 15, 15, 12\nHLT",
            "Beep scale" to
                "MOV A, 440\nloop:\nBEEP A, 250\nADD A, 55\nCMP A, 770\nJLE loop\nHLT",
            "Alphabet" to
                "MOV A, 65\nloop:\nOUTC A\nINC A\nCMP A, 91\nJL loop\nHLT"
        )

        // Searchable reference: name, signature, description
        private val HELP_ENTRIES = listOf(
            Triple("MOV", "r, x", "r = x. Copy a number or another register into r."),
            Triple("ADD", "r, x", "r = r + x"),
            Triple("SUB", "r, x", "r = r - x"),
            Triple("MUL", "r, x", "r = r * x"),
            Triple("DIV", "r, x", "r = r / x (integer, no fractions)"),
            Triple("MOD", "r, x", "r = remainder of r / x. MOD 10 gives the last digit."),
            Triple("INC", "r", "r = r + 1"),
            Triple("DEC", "r", "r = r - 1"),
            Triple("NEG", "r", "r = -r (flip the sign)"),
            Triple("AND", "r, x", "bitwise AND. AND 1 tests even/odd."),
            Triple("OR", "r, x", "bitwise OR"),
            Triple("XOR", "r, x", "bitwise exclusive OR"),
            Triple("NOT", "r", "flip every bit"),
            Triple("SHL", "r, x", "shift bits left (multiply by 2, x times)"),
            Triple("SHR", "r, x", "shift bits right (divide by 2, x times)"),
            Triple("LOAD", "r, addr", "r = memory[addr] (64 slots, 0-63)"),
            Triple("STORE", "addr, x", "memory[addr] = x"),
            Triple("PUSH", "r", "push r onto the stack"),
            Triple("POP", "r", "pop the stack into r"),
            Triple("CMP", "a, b", "compare a with b; sets the flags used by jumps"),
            Triple("JMP", "label", "jump to label, always"),
            Triple("JE", "label", "jump if last CMP was equal (alias JZ)"),
            Triple("JNE", "label", "jump if not equal (alias JNZ)"),
            Triple("JG", "label", "jump if first CMP value was greater"),
            Triple("JGE", "label", "jump if first CMP value was >= "),
            Triple("JL", "label", "jump if first CMP value was less"),
            Triple("JLE", "label", "jump if first CMP value was <= "),
            Triple("CALL", "label", "call a subroutine; remembers the return point"),
            Triple("RET", "", "return to the matching CALL"),
            Triple("IN", "r", "read the next input value into r"),
            Triple("OUT", "x", "print x as a number (starts a new line)"),
            Triple("OUTC", "x", "print the character with ASCII code x (A=65); appends to the current line"),
            Triple("BEEP", "pitch, ms", "play a tone: pitch 100-2000 Hz for 50-2000 ms. Melody levels compare every note's pitch and duration."),
            Triple("CLR", "", "erase the whole canvas"),
            Triple("PEN", "c", "pen color: 0 erase, 1 ink, 2 accent"),
            Triple("PLOT", "x, y", "draw one pixel at (x, y)"),
            Triple("LINE", "x0, y0, x1, y1", "draw a straight line (clipped to the canvas)"),
            Triple("RECT", "x0, y0, x1, y1", "draw a rectangle outline (clipped)"),
            Triple("FILL", "x0, y0, x1, y1", "draw a solid rectangle (clipped)"),
            Triple("PIX", "r, x, y", "read the pixel at (x, y) into r"),
            Triple("NOP", "", "do nothing"),
            Triple("HLT", "", "stop the program"),
            Triple("SYNTAX", "", "x = a number (5, -3) or register (A). Labels end with ':'. Comments start with ';'.")
        )
    }

    private lateinit var level: Level
    private var vm = AssemblyVM()

    private lateinit var editor: EditText
    private lateinit var taskText: TextView
    private lateinit var statusText: TextView
    private lateinit var panelText: TextView
    private lateinit var panelScroll: ScrollView
    private lateinit var pixelGrid: PixelGridView
    private lateinit var outputCard: MaterialCardView
    private lateinit var runButton: MaterialButton
    private lateinit var playButton: MaterialButton
    private lateinit var stepButton: MaterialButton
    private lateinit var ttPrev: MaterialButton
    private lateinit var ttNext: MaterialButton
    private lateinit var ttLabel: TextView
    private val registerViews = linkedMapOf<String, TextView>()

    private var currentTab = 0
    private var steppedCode: String? = null
    private var judgedCode: String? = null
    private var hintShown = false
    private var usedDebuggerThisLevel = false
    private var lastRegisters: Map<String, Int> = emptyMap()
    private var enterPos = -1
    private var runMessage: String? = null
    private var numberFormat = 0                // 0 dec, 1 hex, 2 bin

    // animated playback
    private var playing = false
    private var speedIndex = 0
    private var playCode: String? = null
    private val playHandler = Handler(Looper.getMainLooper())
    private val playTick = object : Runnable {
        override fun run() {
            if (!playing) return
            doPlayTick()
            if (playing) playHandler.postDelayed(this, PLAY_TICK_MS)
        }
    }

    // canvas time-travel
    private class Frame(val canvas: IntArray, val line: Int)
    private val frames = ArrayList<Frame>()
    private var frameIndex = -1

    // undo / redo
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var applyingSnapshot = false

    private var colKeyword = 0; private var colNumber = 0
    private var colComment = 0; private var colLabel = 0
    private var colSuccess = 0; private var colAccent = 0
    private var colError = 0;   private var colSecondary = 0
    private var colLineBg = 0;  private var colErrorBg = 0

    private val draftHandler = Handler(Looper.getMainLooper())
    private val draftSaveRunnable = Runnable {
        ProgressStore.saveDraft(this, level.id, editor.text.toString())
    }

    private val isSandbox: Boolean get() = level.id == 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        level = resolveLevel(intent.getIntExtra(EXTRA_LEVEL_ID, 1))

        loadColors()
        bindViews()
        setupEditor()
        setupButtons()
        setupSnippetChips()

        vm.setStartCanvas(level.initialCanvas)
        vm.reset()                       // so the PIXELS tab shows the starting picture
        selectTab(if (level.isGraphics) 3 else 0)
        updatePanels(-1)
    }

    /** Built-in levels, player-made local levels, the daily challenge and the sandbox all resolve here. */
    private fun resolveLevel(id: Int): Level = when {
        id == 0 -> LevelRepository.sandbox
        id >= CustomLevels.ID_BASE ->
            CustomLevels.get(this, id)?.let(CustomLevels::asLevel)
        id < 0 ->
            DailyChallenge.levelForDay(-id.toLong())
        else ->
            LevelRepository.levels.firstOrNull { it.id == id }
    } ?: LevelRepository.levels.first()

    override fun onPause() {
        super.onPause()
        stopPlay()
        draftHandler.removeCallbacks(draftSaveRunnable)
        ProgressStore.saveDraft(this, level.id, editor.text.toString())
    }

    // ---------- setup ----------

    private fun loadColors() {
        colKeyword = ContextCompat.getColor(this, R.color.keyword)
        colNumber = ContextCompat.getColor(this, R.color.number)
        colComment = ContextCompat.getColor(this, R.color.comment)
        colLabel = ContextCompat.getColor(this, R.color.label)
        colSuccess = ContextCompat.getColor(this, R.color.success)
        colAccent = ContextCompat.getColor(this, R.color.accent)
        colError = ContextCompat.getColor(this, R.color.error)
        colSecondary = ContextCompat.getColor(this, R.color.textSecondary)
        colLineBg = ContextCompat.getColor(this, R.color.lineHighlight)
        colErrorBg = ContextCompat.getColor(this, R.color.errorBg)
    }

    private fun bindViews() {
        editor = findViewById(R.id.codeEditor)
        taskText = findViewById(R.id.taskText)
        statusText = findViewById(R.id.statusText)
        panelText = findViewById(R.id.panelText)
        panelScroll = findViewById(R.id.panelScroll)
        pixelGrid = findViewById(R.id.pixelGrid)
        outputCard = findViewById(R.id.outputCard)
        runButton = findViewById(R.id.runButton)
        playButton = findViewById(R.id.playButton)
        stepButton = findViewById(R.id.stepButton)
        ttPrev = findViewById(R.id.ttPrev)
        ttNext = findViewById(R.id.ttNext)
        ttLabel = findViewById(R.id.ttLabel)

        findViewById<TextView>(R.id.levelTitle).text =
            (if (isSandbox) "\uD83E\uDDEA " else "Level ") + level.title +
            if (level.testCases.isNotEmpty()) "  (${level.testCases.size} test cases)" else ""

        taskText.text = level.task
        taskText.movementMethod = ScrollingMovementMethod()

        registerViews["A"] = findViewById(R.id.regA)
        registerViews["B"] = findViewById(R.id.regB)
        registerViews["C"] = findViewById(R.id.regC)
        registerViews["D"] = findViewById(R.id.regD)
        registerViews.values.forEach { it.setOnLongClickListener { cycleNumberFormat(); true } }

        findViewById<MaterialButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.tabOutput).setOnClickListener { selectTab(0) }
        findViewById<MaterialButton>(R.id.tabStack).setOnClickListener { selectTab(1) }
        findViewById<MaterialButton>(R.id.tabMemory).setOnClickListener { selectTab(2) }
        findViewById<MaterialButton>(R.id.tabPixels).setOnClickListener { selectTab(3) }

        ttPrev.setOnClickListener { scrub(-1) }
        ttNext.setOnClickListener { scrub(1) }
        updateTtLabel()
    }

    private fun setupEditor() {
        editor.textSize = ProgressStore.editorFontSize(this).toFloat()
        editor.setText(ProgressStore.loadDraft(this, level.id) ?: level.starterCode)
        editor.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count == 1 && s?.get(start) == '\n') enterPos = start
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!applyingSnapshot) pushUndo()
            }

            override fun afterTextChanged(s: Editable) {
                if (playing) stopPlay()
                highlightCode(s)
                scheduleDraftSave()
                steppedCode = null
                judgedCode = null
                clearFrames()
                if (enterPos >= 0) {
                    val p = enterPos
                    enterPos = -1
                    autoIndent(s, p)
                }
            }
        })
    }

    private fun setupButtons() {
        runButton.setOnClickListener { runProgram() }
        stepButton.setOnClickListener { stepProgram() }
        playButton.setOnClickListener { togglePlay() }
        playButton.setOnLongClickListener {
            speedIndex = (speedIndex + 1) % PLAY_SPEED_LABELS.size
            toast("Playback speed: ${PLAY_SPEED_LABELS[speedIndex]}")
            true
        }

        val hintButton = findViewById<MaterialButton>(R.id.hintButton)
        if (isSandbox) {
            hintButton.text = "DEMOS"
            hintButton.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Demo programs")
                    .setItems(DEMOS.map { it.first }.toTypedArray()) { _, i ->
                        editor.setText(DEMOS[i].second)
                        toast("Demo loaded — press PLAY or RUN")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            hintButton.setOnClickListener {
                if (!hintShown) {
                    hintShown = true
                    taskText.append("\n\n💡 Hint:\n${level.hint}")
                    hintButton.isEnabled = false
                }
            }
        }

        findViewById<MaterialButton>(R.id.helpButton).setOnClickListener { showSearchableHelp() }

        findViewById<MaterialButton>(R.id.resetButton).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset code")
                .setMessage("Restore the starter code for this level?")
                .setPositiveButton("Reset") { _, _ ->
                    runMessage = null
                    editor.setText(level.starterCode)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupSnippetChips() {
        val row = findViewById<LinearLayout>(R.id.chipRow)

        // undo / redo / font-size tool chips come first
        fun toolChip(label: String, onClick: () -> Unit) {
            val chip = MaterialButton(this).apply {
                text = label
                isAllCaps = false
                textSize = 14f
                minWidth = 0
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                setPadding(dp(12), dp(6), dp(12), dp(6))
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.surfaceLight)
                setTextColor(ContextCompat.getColor(context, R.color.primary))
                typeface = Typeface.MONOSPACE
                cornerRadius = dp(8)
                setOnClickListener { onClick() }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(6)
            row.addView(chip, lp)
        }
        toolChip("\u21B6") { undoEdit() }
        toolChip("\u21B7") { redoEdit() }
        toolChip("Aa") { cycleFontSize() }

        for ((label, snippet) in SNIPPETS) {
            val chip = MaterialButton(this).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                minWidth = 0
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                setPadding(dp(12), dp(6), dp(12), dp(6))
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.surfaceLight)
                setTextColor(ContextCompat.getColor(context, R.color.accent))
                typeface = Typeface.MONOSPACE
                cornerRadius = dp(8)
                setOnClickListener { insertAtCursor(snippet) }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(6)
            row.addView(chip, lp)
        }
    }

    // ---------- undo / redo / font ----------

    private fun pushUndo() {
        undoStack.addLast(editor.text.toString())
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun undoEdit() {
        val prev = undoStack.removeLastOrNull()
        if (prev == null) { toast("Nothing to undo"); return }
        applyingSnapshot = true
        redoStack.addLast(editor.text.toString())
        editor.setText(prev)
        editor.setSelection(prev.length)
        applyingSnapshot = false
        stopPlay()
    }

    private fun redoEdit() {
        val next = redoStack.removeLastOrNull()
        if (next == null) { toast("Nothing to redo"); return }
        applyingSnapshot = true
        undoStack.addLast(editor.text.toString())
        editor.setText(next)
        editor.setSelection(next.length)
        applyingSnapshot = false
        stopPlay()
    }

    private fun cycleFontSize() {
        val current = ProgressStore.editorFontSize(this)
        val idx = (FONT_SIZES.indexOf(current) + 1).let { if (it <= 0) 0 else it } % FONT_SIZES.size
        val size = FONT_SIZES[idx]
        ProgressStore.setEditorFontSize(this, size)
        editor.textSize = size.toFloat()
        toast("Editor font: ${size}sp")
    }

    // ---------- searchable help ----------

    private fun showSearchableHelp() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val filter = EditText(this).apply {
            hint = "\uD83D\uDD0D filter — e.g. \"jump\", \"rect\", \"beep\""
            setSingleLine(true)
        }
        val list = ListView(this).apply { divider = null }
        val all = HELP_ENTRIES.map { (n, sig, d) -> "$n  $sig\n    $d" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList(all))
        list.adapter = adapter
        filter.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                adapter.clear()
                val q = s.toString().trim().lowercase()
                adapter.addAll(if (q.isEmpty()) all else all.filter { it.lowercase().contains(q) })
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        box.addView(filter)
        box.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(380)
        ))
        MaterialAlertDialogBuilder(this)
            .setTitle("Instruction set")
            .setView(box)
            .setPositiveButton("Got it", null)
            .show()
    }

    // ---------- running / playing / stepping / judging ----------

    private fun maybeShowCanvas(code: String) {
        if (level.isGraphics || GRAPHICS_REGEX.containsMatchIn(code)) selectTab(3)
    }

    private fun runProgram() {
        stopPlay()
        val code = editor.text.toString()
        runButton.isEnabled = false
        runMessage = null
        panelText.setTextColor(colSecondary)
        highlightEditorLine(-1, colLineBg)
        statusText.text = "running…"
        if (!isSandbox) ProgressStore.recordRun(this, level.id)
        maybeShowCanvas(code)
        Thread {
            val result = runAndJudge(code)
            runOnUiThread {
                runButton.isEnabled = true
                steppedCode = code
                judgedCode = code
                vm = result.vm
                onFinished(result)
                autoHear(result.vm)
            }
        }.start()
    }

    private fun togglePlay() {
        if (playing) { stopPlay(); return }

        val code = editor.text.toString()
        vm.setStartCanvas(level.initialCanvas)
        if (!vm.load(code)) {
            showError(vm.error ?: "Parse error", vm.errorLine)
            return
        }
        if (level.testCases.isNotEmpty()) vm.setInput(level.testCases[0].input)

        playing = true
        playCode = code
        runMessage = null
        runButton.isEnabled = false
        stepButton.isEnabled = false
        playButton.text = "⏸ PAUSE"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        clearFrames()
        maybeShowCanvas(code)
        statusText.text = "playing… (${PLAY_SPEED_LABELS[speedIndex]})"
        updatePanels(-1)
        playHandler.postDelayed(playTick, PLAY_TICK_MS)
    }

    private fun stopPlay() {
        if (!playing) return
        playing = false
        playHandler.removeCallbacks(playTick)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playButton.text = "⏯ PLAY"
        runButton.isEnabled = true
        stepButton.isEnabled = true
    }

    private fun doPlayTick() {
        val newBeeps = mutableListOf<Beep>()
        var ran = 0
        while (ran < PLAY_STEPS_PER_TICK[speedIndex] && vm.canStep()) {
            val beepsBefore = vm.beeps.size
            vm.step()
            ran++
            if (vm.beeps.size > beepsBefore) newBeeps.add(vm.beeps.last())
        }
        if (vm.error != null) {
            stopPlay()
            showError(vm.error!!, vm.errorLine)
            return
        }
        // play notes captured this tick, spaced by their durations (no cacophony at high speed)
        if (newBeeps.isNotEmpty() && ProgressStore.soundEnabled(this)) {
            var off = 0L
            for (b in newBeeps) {
                playHandler.postDelayed({ BeepPlayer.play(b.pitchHz, b.durationMs) }, off)
                off += b.durationMs + 20L
            }
        }
        updatePanels(vm.currentLine)
        if (!vm.canStep()) {
            stopPlay()
            val code = playCode ?: return
            steppedCode = code
            judgedCode = code
            val result = runAndJudge(code)
            vm = result.vm
            onFinished(result)
        }
    }

    private fun stepProgram() {
        stopPlay()
        val code = editor.text.toString()
        if (steppedCode != code) {
            runMessage = null
            vm.setStartCanvas(level.initialCanvas)
            if (!vm.load(code)) {
                showError(vm.error ?: "Parse error", vm.errorLine)
                steppedCode = null
                return
            }
            if (level.testCases.isNotEmpty()) {
                vm.setInput(level.testCases[0].input)
            }
            steppedCode = code
            clearFrames()
            maybeShowCanvas(code)
        }
        if (!vm.canStep()) {
            when {
                vm.error != null -> showError(vm.error!!, vm.errorLine)
                judgedCode == code ->
                    statusText.text = "program finished — edit the code to run/step again"
                else -> {
                    val result = runAndJudge(code)
                    vm = result.vm
                    onFinished(result)
                }
            }
            return
        }
        usedDebuggerThisLevel = true
        vm.step()
        if (vm.error != null) {
            showError(vm.error!!, vm.errorLine)
            updatePanels(-1)
            return
        }
        updatePanels(vm.currentLine)
        if (!vm.canStep()) {
            judgedCode = code
            val result = runAndJudge(code)
            vm = result.vm
            onFinished(result)
        }
    }

    /** Rules first, then every hidden test case + canvas target + melody, then custom validation. */
    private fun runAndJudge(code: String): RunResult {
        for (banned in level.bannedSnippets) {
            if (code.contains(banned, ignoreCase = true)) {
                val snapshot = AssemblyVM().apply {
                    setStartCanvas(level.initialCanvas); run(code)
                }
                return RunResult.Success(
                    snapshot, false, "Rule broken: \"$banned\" may not appear in your code."
                )
            }
        }
        level.requiredRegex?.let {
            if (!it.containsMatchIn(code)) {
                val snapshot = AssemblyVM().apply {
                    setStartCanvas(level.initialCanvas); run(code)
                }
                return RunResult.Success(snapshot, false, level.requiredMessage)
            }
        }

        if (level.testCases.isEmpty()) {
            val v = AssemblyVM()
            v.setStartCanvas(level.initialCanvas)
            if (!v.run(code)) return RunResult.Error(v, v.error ?: "Unknown error", v.errorLine)
            canvasFailMessage(v)?.let { return RunResult.Success(v, false, it) }
            beepsFailMessage(v)?.let { return RunResult.Success(v, false, it) }
            val msg = level.validate(v, code)
            return RunResult.Success(v, msg == null, msg ?: "All checks passed!")
        }

        var lastVm: AssemblyVM? = null
        for ((i, tc) in level.testCases.withIndex()) {
            val caseVm = AssemblyVM()
            caseVm.setStartCanvas(level.initialCanvas)
            caseVm.setInput(tc.input)
            if (!caseVm.run(code)) {
                return RunResult.Error(caseVm, "Case ${i + 1}: ${caseVm.error}", caseVm.errorLine)
            }
            if (caseVm.output != tc.expected) {
                return RunResult.Success(
                    caseVm, false,
                    "Test case ${i + 1} of ${level.testCases.size} failed." +
                    "\nInput: [${tc.input.joinToString(", ")}]" +
                    "\nExpected: ${tc.expected.joinToString(", ")}" +
                    "\nGot: ${caseVm.output.joinToString(", ").ifEmpty { "(nothing)" }}"
                )
            }
            canvasFailMessage(caseVm)?.let {
                return RunResult.Success(caseVm, false, "Test case ${i + 1}: $it")
            }
            beepsFailMessage(caseVm)?.let {
                return RunResult.Success(caseVm, false, "Test case ${i + 1}: $it")
            }
            lastVm = caseVm
        }
        val v = lastVm!!
        val msg = level.validate(v, code)
        return if (msg == null) {
            RunResult.Success(v, true, "All ${level.testCases.size} test cases passed!")
        } else {
            RunResult.Success(v, false, msg)
        }
    }

    /** Returns a failure message if the canvas doesn't match the level's target picture. */
    private fun canvasFailMessage(v: AssemblyVM): String? {
        val expected = level.expectedCanvas ?: return null
        val got = v.renderCanvas()
        if (got == expected) return null
        val expN = expected.sumOf { row -> row.count { it != '.' } }
        val gotN = got.sumOf { row -> row.count { it != '.' } }
        var first = ""
        outer@ for (y in 0 until AssemblyVM.CANVAS_SIZE) {
            for (x in 0 until AssemblyVM.CANVAS_SIZE) {
                if (expected[y][x] != got[y][x]) {
                    first = "First mismatch at x=$x, y=$y " +
                            "(expected '${expected[y][x]}', drew '${got[y][x]}')"
                    break@outer
                }
            }
        }
        return "Picture doesn't match — $expN pixels expected, $gotN drawn. $first. Check the PIXELS tab."
    }

    /** Returns a failure message if the played melody doesn't match the level's target. */
    private fun beepsFailMessage(v: AssemblyVM): String? {
        val expected = level.expectedBeeps
        if (expected.isEmpty()) return null
        val got = v.beeps
        val matches = got.size == expected.size &&
            got.zip(expected).all { (g, e) -> g.pitchHz == e.pitchHz && g.durationMs == e.durationMs }
        if (matches) return null
        fun fmt(b: Beep) = "${b.pitchHz}Hz/${b.durationMs}ms"
        return "Melody doesn't match." +
            "\nExpected ${expected.size} note(s): ${expected.joinToString(", ") { fmt(it) }}" +
            "\nGot ${got.size} note(s): ${got.joinToString(", ") { fmt(it) }.ifEmpty { "(silence)" }}" +
            "\nCheck the ♪ list in the OUT tab."
    }

    /** RUN auto-plays the recorded melody once so the player can hear it. */
    private fun autoHear(v: AssemblyVM) {
        if (v.beeps.isEmpty() || !ProgressStore.soundEnabled(this)) return
        BeepPlayer.playSequence(v.beeps)
        if (v.beeps.size > 1) toast("♪ Playing ${v.beeps.size} notes…")
    }

    private fun onFinished(result: RunResult) {
        when (result) {
            is RunResult.Error -> showError(result.message, result.line)
            is RunResult.Success -> {
                updatePanels(-1)
                if (isSandbox) {
                    runMessage = "✓ Ran in ${result.vm.steps} steps — edit the code and go again"
                    panelText.setTextColor(colSuccess)
                    selectTab(0)
                    return
                }
                if (result.passed) {
                    val steps = result.vm.steps
                    val stars = level.starsFor(steps)
                    val codeUpper = editor.text.toString().uppercase()
                    val usedRectFill = Regex("\\b(RECT|FILL)\\b").containsMatchIn(codeUpper)
                    ProgressStore.recordBest(this, level.id, steps)
                    ProgressStore.complete(this, level.id, stars, hintShown, usedDebuggerThisLevel)
                    if (level.id < 0) {
                        ProgressStore.noteDailyWin(this, DailyChallenge.todayId())
                    }
                    val distinctOpcodes = codeUpper
                        .split(Regex("[\\s,]+"))
                        .filter { it in AssemblyVM.OPCODES }
                        .toSet().size
                    val newAchievements = Achievements.checkAfterWin(
                        this, stars, hintShown, usedDebuggerThisLevel,
                        distinctOpcodes, level.isGraphics, usedRectFill, level.isMelody
                    )
                    for (a in newAchievements) {
                        Toast.makeText(this, "🏆 Achievement unlocked: ${a.title}", Toast.LENGTH_LONG).show()
                    }
                    celebrate()
                    showWinDialog(stars, result.judgeMessage, steps)
                } else {
                    runMessage = "✖ FAILED — ${result.judgeMessage}"
                    panelText.setTextColor(colError)
                    selectTab(0)
                    vibrate(60)
                }
            }
        }
    }

    private fun showWinDialog(stars: Int, message: String, steps: Int) {
        val starLine = "★".repeat(stars) + "☆".repeat(3 - stars)
        val nextId: Int? = when {
            level.id >= CustomLevels.ID_BASE -> CustomLevels.nextAfter(this, level.id)?.id
            level.id < 0 -> null
            else -> LevelRepository.levels.firstOrNull { it.id == level.id + 1 }?.id
        }
        val streakNote = if (level.id < 0)
            "\n\n🔥 Daily streak: ${ProgressStore.dailyStreak(this)} day(s)" else ""
        MaterialAlertDialogBuilder(this)
            .setTitle("🎉 Level complete!")
            .setMessage("$starLine\n\n$message\n\nSolved in $steps steps (3★ par: ${level.parSteps})$streakNote")
            .setCancelable(false)
            .setPositiveButton(if (nextId != null) "Next level ▶" else "Finish") { _, _ ->
                nextId?.let { id ->
                    startActivity(
                        Intent(this, GameActivity::class.java).putExtra(EXTRA_LEVEL_ID, id)
                    )
                }
                finish()
            }
            .setNegativeButton("Stay here", null)
            .show()
    }

    private fun showError(message: String, line: Int? = null) {
        runMessage = "✖ $message"
        panelText.setTextColor(colError)
        selectTab(0)
        updatePanels(-1)
        highlightEditorLine(line ?: -1, colErrorBg)
        vibrate(80)
    }

    // ---------- canvas time-travel ----------

    private fun clearFrames() {
        frames.clear()
        frameIndex = -1
        updateTtLabel()
    }

    private fun recordFrame() {
        if (frames.size >= MAX_FRAMES) {
            // decimate: keep every other frame so long runs stay scrub-able
            val kept = ArrayList<Frame>(frames.size / 2 + 1)
            for (i in frames.indices step 2) kept.add(frames[i])
            frames.clear()
            frames.addAll(kept)
        }
        frames.add(Frame(vm.canvas.copyOf(), vm.currentLine))
        frameIndex = frames.size - 1
        updateTtLabel()
    }

    private fun scrub(delta: Int) {
        if (playing) stopPlay()
        if (frames.isEmpty()) { toast("Run or play the program first"); return }
        frameIndex = (frameIndex + delta).coerceIn(0, frames.size - 1)
        val f = frames[frameIndex]
        pixelGrid.setPixels(f.canvas)
        highlightEditorLine(f.line, colLineBg)
        updateTtLabel()
    }

    private fun updateTtLabel() {
        ttLabel.text = if (frames.isEmpty()) "—"
            else "frame ${frameIndex + 1}/${frames.size}"
    }

    // ---------- UI state / panels ----------

    private fun fmt(v: Int): String = when (numberFormat) {
        1 -> "0x" + "%04X".format(v and 0xFFFF)
        2 -> "0b" + (v and 0xFF).toString(2).padStart(8, '0')
        else -> v.toString()
    }

    private fun cycleNumberFormat() {
        numberFormat = (numberFormat + 1) % 3
        refreshRegisters()
        toast("Registers: ${arrayOf("decimal", "hex (low 16 bits)", "binary (low 8 bits)")[numberFormat]}")
    }

    private fun refreshRegisters() {
        for ((name, view) in registerViews) {
            val value = vm.registers.getValue(name)
            view.text = "$name = ${fmt(value)}"
            val changed = name in lastRegisters && lastRegisters[name] != value
            view.setTextColor(if (changed) colSuccess else colAccent)
            if (changed) view.postDelayed({ view.setTextColor(colAccent) }, 500)
        }
        lastRegisters = vm.registers.toMap()
    }

    private fun updatePanels(highlightLine: Int) {
        refreshRegisters()
        statusText.text = buildString {
            append("steps ${vm.steps}   •   ZF ${if (vm.flagZero) 1 else 0}   LF ${if (vm.flagLess) 1 else 0}")
            if (vm.inputs.isNotEmpty()) append("   •   in ${vm.inputRemaining()} left")
        }
        pixelGrid.setPixels(vm.canvas)
        recordFrame()
        renderTab()
        highlightEditorLine(highlightLine, colLineBg)
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val ids = listOf(R.id.tabOutput, R.id.tabStack, R.id.tabMemory, R.id.tabPixels)
        ids.forEachIndexed { i, id ->
            val b = findViewById<MaterialButton>(id)
            b.backgroundTintList = ContextCompat.getColorStateList(
                this, if (i == index) R.color.surfaceLight else android.R.color.transparent
            )
            b.setTextColor(
                ContextCompat.getColor(this, if (i == index) R.color.primary else R.color.textSecondary)
            )
        }
        panelScroll.visibility = if (index == 3) View.GONE else View.VISIBLE
        pixelGrid.visibility = if (index == 3) View.VISIBLE else View.GONE
        findViewById<View>(R.id.ttBar).visibility = if (index == 3) View.VISIBLE else View.GONE
        renderTab()
    }

    private fun renderTab() {
        val sb = StringBuilder()
        when (currentTab) {
            0 -> {
                runMessage?.let { sb.append(it).append("\n\n") }
                if (vm.inputs.isNotEmpty()) sb.append("input: [${vm.inputs.joinToString(", ")}]\n")
                if (vm.output.isEmpty()) sb.append("(no output)")
                else vm.output.forEach { sb.append("» ").append(it).append('\n') }
                if (vm.beeps.isNotEmpty()) {
                    sb.append("\n♪ notes:\n")
                    vm.beeps.forEach { sb.append("  ♪ ${it.pitchHz} Hz · ${it.durationMs} ms\n") }
                }
            }
            1 -> {
                val st = vm.stackSnapshot()
                if (st.isEmpty()) sb.append("(stack is empty)")
                else st.asReversed().forEachIndexed { i, v ->
                    sb.append(if (i == 0) "top → $v" else "       $v").append('\n')
                }
            }
            2 -> {
                for (rowStart in 0 until AssemblyVM.MEMORY_SIZE step 8) {
                    sb.append("%3d:".format(rowStart))
                    for (i in rowStart until rowStart + 8) sb.append("%5d".format(vm.memory[i]))
                    sb.append('\n')
                }
            }
            else -> sb.append("")   // PIXELS tab draws itself
        }
        panelText.text = sb.toString().trimEnd()
    }

    private fun highlightEditorLine(lineNo: Int, bgColor: Int) {
        val text = editor.text ?: return
        for (span in text.getSpans(0, text.length, BackgroundColorSpan::class.java)) {
            text.removeSpan(span)
        }
        if (lineNo <= 0) return
        val s = text.toString()
        var idx = 0
        var line = 1
        while (idx < s.length && line < lineNo) {
            if (s[idx] == '\n') line++
            idx++
        }
        if (line < lineNo) return
        var end = s.indexOf('\n', idx)
        if (end == -1) end = s.length
        if (end == idx && idx < s.length) end = idx + 1
        text.setSpan(BackgroundColorSpan(bgColor), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // keep the executing line visible while stepping/playing
        if (!editor.hasFocus()) {
            editor.setSelection(idx.coerceAtMost(text.length))
            editor.post {
                val layout = editor.layout ?: return@post
                val l = layout.getLineForOffset(idx)
                val target = (layout.getLineTop(l) - editor.height / 3).coerceAtLeast(0)
                editor.scrollTo(0, target)
            }
        }
    }

    // ---------- editing helpers ----------

    private fun highlightCode(text: Editable) {
        for (span in text.getSpans(0, text.length, ForegroundColorSpan::class.java)) text.removeSpan(span)
        for (span in text.getSpans(0, text.length, BackgroundColorSpan::class.java)) text.removeSpan(span)
        var index = 0
        for (line in text.toString().lines()) {
            val lineStart = index
            val lineEnd = index + line.length
            val commentIdx = line.indexOf(';')
            val codePart = if (commentIdx >= 0) line.substring(0, commentIdx) else line

            if (commentIdx >= 0) {
                text.setSpan(
                    ForegroundColorSpan(colComment), lineStart + commentIdx, lineEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            for (match in TOKEN_REGEX.findAll(codePart)) {
                val token = match.value
                val color = when {
                    token.endsWith(":") -> colLabel
                    token.uppercase() in AssemblyVM.OPCODES -> colKeyword
                    token.toIntOrNull() != null -> colNumber
                    else -> null
                }
                if (color != null) {
                    text.setSpan(
                        ForegroundColorSpan(color),
                        lineStart + match.range.first,
                        lineStart + match.range.last + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            index = lineEnd + 1
        }
    }

    private fun insertAtCursor(snippet: String) {
        val editable = editor.editableText ?: return
        var start = editor.selectionStart
        var end = editor.selectionEnd
        if (start < 0 || end < 0 || start > editable.length || end > editable.length) {
            start = editable.length
            end = start
        }
        val lo = minOf(start, end)
        editable.replace(lo, maxOf(start, end), snippet)
        editor.setSelection(lo + snippet.length)
    }

    private fun autoIndent(s: Editable, newlinePos: Int) {
        if (newlinePos <= 0 || newlinePos >= s.length + 1) return
        val text = s.toString()
        val lineStart = (text.lastIndexOf('\n', newlinePos - 1) + 1).coerceAtLeast(0)
        if (lineStart > newlinePos) return
        val prevLine = text.substring(lineStart, newlinePos)
        if (prevLine.trimEnd().endsWith(":")) return
        val indent = prevLine.takeWhile { it == ' ' }
        if (indent.isNotEmpty() && newlinePos + 1 <= s.length) s.insert(newlinePos + 1, indent)
    }

    private fun scheduleDraftSave() {
        draftHandler.removeCallbacks(draftSaveRunnable)
        draftHandler.postDelayed(draftSaveRunnable, 500)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && editor.hasFocus()) {
            when {
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_Z && !event.isShiftPressed -> {
                    undoEdit(); return true
                }
                event.isCtrlPressed &&
                    (event.keyCode == KeyEvent.KEYCODE_Y ||
                        (event.keyCode == KeyEvent.KEYCODE_Z && event.isShiftPressed)) -> {
                    redoEdit(); return true
                }
                event.keyCode == KeyEvent.KEYCODE_TAB -> {
                    insertAtCursor("    "); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------- feedback ----------

    private fun celebrate() {
        vibrate(60)
        playSuccessTone()
        outputCard.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).withEndAction {
            outputCard.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    private fun playSuccessTone() {
        if (!ProgressStore.soundEnabled(this)) return
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 400)
        } catch (_: Exception) { /* audio not available — ignore */ }
    }

    private fun vibrate(ms: Long) {
        if (!ProgressStore.vibrationEnabled(this)) return
        try {
            val vibrator = getSystemService(Vibrator::class.java) ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (_: Exception) { /* ignore */ }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
