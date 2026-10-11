package com.example.assemblygame

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.assemblygame.game.AssemblyVM
import com.example.assemblygame.game.CustomLevels
import com.example.assemblygame.ui.PixelPaintView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LevelEditorActivity : AppCompatActivity() {

    companion object { const val EXTRA_CUSTOM_ID = "extra_custom_id" }

    private lateinit var etTitle: EditText
    private lateinit var etTask: EditText
    private lateinit var etHint: EditText
    private lateinit var etStarter: EditText
    private lateinit var etPar: EditText
    private lateinit var etBanned: EditText
    private lateinit var etCanvas: EditText
    private lateinit var paintView: PixelPaintView
    private val caseInputs = mutableListOf<EditText>()
    private val caseExpected = mutableListOf<EditText>()
    private var editingId = -1
    private var syncingCanvasFields = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_level_editor)

        bindViews()
        setupCanvasEditors()

        editingId = intent.getIntExtra(EXTRA_CUSTOM_ID, -1)
        val existing = if (editingId >= CustomLevels.ID_BASE) CustomLevels.get(this, editingId) else null
        if (existing != null) {
            findViewById<TextView>(R.id.editorHeading).text = "Edit level"
            fill(existing)
        } else {
            editingId = -1
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            saveAnd { toast("Saved \"${it.title}\" — find it at the bottom of the level list") }
        }
        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            saveAnd { entry ->
                copyToClipboard(CustomLevels.encode(entry))
                toast("Level code copied — share it anywhere you like")
            }
        }
        findViewById<MaterialButton>(R.id.btnPlay).setOnClickListener {
            saveAnd { entry ->
                startActivity(
                    Intent(this, GameActivity::class.java)
                        .putExtra(GameActivity.EXTRA_LEVEL_ID, entry.id)
                )
                finish()
            }
        }
        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener { testSolution() }
    }

    private fun bindViews() {
        etTitle = findViewById(R.id.etTitle)
        etTask = findViewById(R.id.etTask)
        etHint = findViewById(R.id.etHint)
        etStarter = findViewById(R.id.etStarter)
        etPar = findViewById(R.id.etPar)
        etBanned = findViewById(R.id.etBanned)
        etCanvas = findViewById(R.id.etCanvas)
        paintView = findViewById(R.id.pixelPaint)
        caseInputs += listOf(findViewById(R.id.c1in), findViewById(R.id.c2in), findViewById(R.id.c3in))
        caseExpected += listOf(findViewById(R.id.c1ex), findViewById(R.id.c2ex), findViewById(R.id.c3ex))
    }

    /** The paint grid and the text field stay in sync; either can be used to edit. */
    private fun setupCanvasEditors() {
        paintView.setOnPatternChanged { pattern ->
            if (!syncingCanvasFields) {
                syncingCanvasFields = true
                etCanvas.setText(pattern.joinToString("\n"))
                syncingCanvasFields = false
            }
        }
        etCanvas.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (syncingCanvasFields) return
                syncingCanvasFields = true
                val lines = s.toString().lines().map { it.padEnd(16, '.').take(16) }
                paintView.setPattern(List(16) { i -> lines.getOrElse(i) { ".".repeat(16) } })
                syncingCanvasFields = false
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        findViewById<MaterialButton>(R.id.btnClearCanvas).setOnClickListener {
            paintView.clear()
            etCanvas.setText("")
        }
    }

    private fun fill(e: CustomLevels.Entry) {
        etTitle.setText(e.title)
        etTask.setText(e.task)
        etHint.setText(e.hint)
        etStarter.setText(e.starterCode)
        etPar.setText(e.parSteps.toString())
        etBanned.setText(e.banned.joinToString(", "))
        for (i in 0 until 3) {
            if (i < e.tests.size) {
                caseInputs[i].setText(e.tests[i].first.joinToString(", "))
                caseExpected[i].setText(e.tests[i].second.joinToString("\n"))
            }
        }
        etCanvas.setText(e.canvas?.joinToString("\n") ?: "")
    }

    private fun collect(): CustomLevels.Entry? {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) { toast("Give your level a title"); return null }
        if (title.length > 40) { toast("Title too long (max 40 characters)"); return null }
        val task = etTask.text.toString().trim()
        if (task.isEmpty()) { toast("Describe what the player must do"); return null }
        val hint = etHint.text.toString().trim()
        val starter = etStarter.text.toString()
        val par = etPar.text.toString().trim().toIntOrNull()
        if (par == null || par < 1) { toast("Par steps must be a positive number"); return null }
        val banned = etBanned.text.toString().split(',')
            .map { it.trim() }.filter { it.isNotEmpty() }.take(5)

        var canvas: List<String>? = null
        val canvasLines = etCanvas.text.toString().lines()
            .map { it.take(16) }
            .filter { it.isNotBlank() }
        if (canvasLines.isNotEmpty()) {
            if (canvasLines.size != 16 || canvasLines.any { it.length != 16 }) {
                toast("Pixel art must be exactly 16 lines of 16 characters"); return null
            }
            if (canvasLines.any { row -> row.any { it !in ".#+" } }) {
                toast("Pixel art may only contain '.', '#' and '+'"); return null
            }
            canvas = canvasLines
        }

        val tests = mutableListOf<Pair<List<Int>, List<String>>>()
        for (i in 0 until 3) {
            val inText = caseInputs[i].text.toString().trim()
            val exText = caseExpected[i].text.toString()
            if (inText.isEmpty() && exText.isBlank()) continue
            val input = mutableListOf<Int>()
            for (part in inText.split(',')) {
                val p = part.trim()
                if (p.isEmpty()) continue
                val v = p.toIntOrNull()
                if (v == null) { toast("Case ${i + 1}: \"$p\" is not a whole number"); return null }
                input.add(v)
            }
            if (input.size > 8) { toast("Case ${i + 1}: max 8 input values"); return null }
            val expected = exText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (expected.isEmpty()) { toast("Case ${i + 1}: expected output is missing"); return null }
            tests.add(input to expected)
        }
        if (tests.isEmpty()) { toast("Add at least one test case"); return null }
        return CustomLevels.Entry(-1, title, task, hint, starter, par, banned, tests, canvas)
    }

    private fun saveAnd(after: (CustomLevels.Entry) -> Unit) {
        val entry = collect() ?: return
        val id = if (editingId >= CustomLevels.ID_BASE) editingId else CustomLevels.newId(this)
        val saved = entry.copy(id = id)
        CustomLevels.save(this, saved)
        after(saved)
    }

    // ---------- verify a solution locally ----------

    private fun testSolution() {
        val entry = collect() ?: return
        val input = EditText(this).apply {
            hint = "; your solution here"
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setLines(8)
            typeface = Typeface.MONOSPACE
            setText("")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Test a solution")
            .setMessage("Runs your code against every test case and the pixel target. Nothing is saved.")
            .setView(input)
            .setPositiveButton("Run") { _, _ ->
                val code = input.text.toString()
                toast("Testing…")
                Thread {
                    val report = verify(entry, code)
                    runOnUiThread {
                        val perfect = report.startsWith("${entry.tests.size}/${entry.tests.size}")
                        MaterialAlertDialogBuilder(this)
                            .setTitle(if (perfect) "✔ Solvable!" else "⚠ Check results")
                            .setMessage(report)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verify(entry: CustomLevels.Entry, code: String): String {
        val issues = StringBuilder()
        var passed = 0
        for (b in entry.banned) {
            if (code.contains(b, ignoreCase = true)) {
                issues.append("✖ Rule: \"$b\" is banned but appears in the code\n")
            }
        }
        val size = AssemblyVM.CANVAS_SIZE
        for ((i, t) in entry.tests.withIndex()) {
            val vm = AssemblyVM()
            vm.setInput(t.first)
            if (!vm.run(code)) {
                issues.append("✖ Case ${i + 1}: ${vm.error}\n")
                continue
            }
            val outOk = vm.output == t.second
            val canvasOk = entry.canvas?.let { target ->
                val got = vm.renderCanvas()
                var diff = 0; var first = ""
                outer@ for (y in 0 until size) for (x in 0 until size) {
                    if (target[y][x] != got[y][x]) {
                        diff++
                        if (first.isEmpty()) first = "x=$x, y=$y"
                    }
                }
                if (diff == 0) true
                else {
                    issues.append("✖ Case ${i + 1}: picture differs in $diff pixel(s), first at $first\n")
                    false
                }
            } ?: true
            if (outOk && canvasOk) passed++
            else if (!outOk) {
                issues.append(
                    "✖ Case ${i + 1}: expected [${t.second.joinToString(", ")}] " +
                    "got [${vm.output.joinToString(", ").ifEmpty { "(nothing)" }}]\n"
                )
            }
        }
        val head = "$passed/${entry.tests.size} test cases pass"
        return if (issues.isEmpty()) "$head\n\n✔ The level is solvable — nice!" else "$head\n\n$issues"
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("ASM Quest level", text))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
