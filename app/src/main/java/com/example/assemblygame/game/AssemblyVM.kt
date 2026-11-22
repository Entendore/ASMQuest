package com.example.assemblygame.game

/**
 * A tiny, safe virtual machine for a teaching assembly language.
 *
 * Instructions:
 *   Data:     MOV, ADD, SUB, MUL, DIV, MOD, INC, DEC, NEG
 *   Bits:     AND, OR, XOR, NOT, SHL, SHR
 *   Memory:   LOAD r, addr      STORE addr, x        (64 cells)
 *   Stack:    PUSH r, POP r
 *   Flow:     CMP, JMP, JE/JZ, JNE/JNZ, JG, JGE, JL, JLE, CALL, RET, NOP, HLT
 *   I/O:      IN r, OUT x, OUTC x
 *   Graphics: CLR, PEN c, PLOT x, y, LINE x0,y0,x1,y1,
 *             RECT x0,y0,x1,y1, FILL x0,y0,x1,y1, PIX r, x, y   (16x16 canvas)
 *   Sound:    BEEP pitch, ms
 *
 * Operands "x" may be a number or a register. Labels end with ':'.
 * Comments start with ';'. Supports single-stepping for debuggers.
 */
class AssemblyVM {

    companion object {
        val REGISTER_NAMES = listOf("A", "B", "C", "D")
        val OPCODES = setOf(
            "MOV", "ADD", "SUB", "MUL", "DIV", "MOD", "INC", "DEC", "NEG",
            "AND", "OR", "XOR", "NOT", "SHL", "SHR",
            "LOAD", "STORE", "PUSH", "POP",
            "CMP", "JMP", "JE", "JZ", "JNE", "JNZ", "JG", "JGE", "JL", "JLE",
            "CALL", "RET",
            "IN", "OUT", "OUTC", "CLR", "PEN", "PLOT", "PIX", "LINE", "RECT", "FILL",
            "BEEP", "NOP", "HLT"
        )
        private val TWO_ARG = setOf(
            "MOV", "ADD", "SUB", "MUL", "DIV", "MOD",
            "AND", "OR", "XOR", "SHL", "SHR", "LOAD", "STORE", "CMP", "BEEP"
        )
        private val THREE_ARG = setOf("PIX")
        private val FOUR_ARG = setOf("LINE", "RECT", "FILL")
        private val ONE_ARG = setOf(
            "INC", "DEC", "NEG", "NOT", "PUSH", "POP",
            "IN", "OUT", "OUTC", "PEN",
            "JMP", "JE", "JZ", "JNE", "JNZ", "JG", "JGE", "JL", "JLE", "CALL"
        )

        const val MEMORY_SIZE = 64
        const val CANVAS_SIZE = 16
        const val MAX_STEPS = 200_000
        const val MAX_OUTPUT_LINES = 500
        const val MAX_LINE_LENGTH = 200
        const val MAX_STACK = 256
        const val BEEP_MIN_PITCH = 100
        const val BEEP_MAX_PITCH = 2000
        const val BEEP_MIN_MS = 50
        const val BEEP_MAX_MS = 2000
        const val MAX_BEEPS = 100
        private val LABEL_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }

    class Instruction(val op: String, val args: List<String>, val lineNo: Int)

    val registers = linkedMapOf("A" to 0, "B" to 0, "C" to 0, "D" to 0)
    val memory = IntArray(MEMORY_SIZE)
    val canvas = IntArray(CANVAS_SIZE * CANVAS_SIZE)   // 0 empty, 1 ink, 2 accent
    val output = mutableListOf<String>()
    val inputs = mutableListOf<Int>()
    val beeps = mutableListOf<Beep>()

    var halted = false; private set
    var error: String? = null; private set
    var errorLine = -1; private set
    var steps = 0; private set
    var flagZero = false; private set
    var flagLess = false; private set
    var currentLine = -1; private set

    private val stack = ArrayDeque<Int>()
    private var inputPos = 0
    private var pen = 1
    private var startCanvas: List<String>? = null      // pattern applied on every reset
    private var program: List<Instruction> = emptyList()
    private var labels: Map<String, Int> = emptyMap()
    private var pc = 0
    private var loaded = false

    /** Values that IN will hand out, one per call. */
    fun setInput(values: List<Int>) {
        inputs.clear()
        inputs.addAll(values)
        inputPos = 0
    }

    fun inputRemaining(): Int = inputs.size - inputPos

    /** Optional starting picture ('.' empty, '#' ink, '+' accent) — applied on every reset. */
    fun setStartCanvas(pattern: List<String>?) { startCanvas = pattern }

    fun reset() {
        for (r in registers.keys) registers[r] = 0
        memory.fill(0)
        canvas.fill(0)
        output.clear()
        beeps.clear()
        stack.clear()
        pen = 1
        halted = false; error = null; errorLine = -1; steps = 0
        flagZero = false; flagLess = false; currentLine = -1
        pc = 0; loaded = false; inputPos = 0
        startCanvas?.let { pattern ->
            for (y in 0 until minOf(pattern.size, CANVAS_SIZE)) {
                val row = pattern[y]
                for (x in 0 until minOf(row.length, CANVAS_SIZE)) {
                    canvas[y * CANVAS_SIZE + x] = when (row[x]) { '#' -> 1; '+' -> 2; else -> 0 }
                }
            }
        }
    }

    /** Parses and loads a program. Returns false and sets [error] on a syntax problem. */
    fun load(code: String): Boolean {
        reset()
        val parsed = parse(code) ?: return false
        program = parsed.first
        labels = parsed.second
        loaded = true
        return true
    }

    fun canStep(): Boolean =
        loaded && !halted && error == null && steps < MAX_STEPS && pc in program.indices

    /** Executes exactly one instruction. Returns false when the program can't continue. */
    fun step(): Boolean {
        if (!canStep()) {
            if (loaded && !halted && error == null && pc !in program.indices) halted = true
            return false
        }
        val ins = program[pc]
        currentLine = ins.lineNo
        val target = execute(ins)
        steps++
        if (error != null) return false
        if (!halted) pc = target ?: (pc + 1)
        return true
    }

    /** Runs the program to the end. Returns true if it finished without errors. */
    fun run(code: String): Boolean {
        if (!load(code)) return false
        while (step()) { /* keep going */ }
        return error == null
    }

    fun stackSnapshot(): List<Int> = stack.toList()

    /** Current canvas as 16 strings of 16 chars ('.', '#', '+'). */
    fun renderCanvas(): List<String> {
        val chars = charArrayOf('.', '#', '+')
        return List(CANVAS_SIZE) { y ->
            CharArray(CANVAS_SIZE) { x -> chars[canvas[y * CANVAS_SIZE + x]] }.concatToString()
        }
    }

    // ---------- graphics helpers ----------

    /** Plots with the current pen, silently clipping to the canvas (raster behavior). */
    private fun plotClipped(x: Int, y: Int) {
        if (x in 0 until CANVAS_SIZE && y in 0 until CANVAS_SIZE) {
            canvas[y * CANVAS_SIZE + x] = pen
        }
    }

    /** Bresenham line, clipped to the canvas. */
    private fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int) {
        var x = x0; var y = y0
        val dx = Math.abs(x1 - x0); val dy = -Math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var guard = 0
        while (guard++ < 512) {
            plotClipped(x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Outlined (filled=false) or filled rectangle, clipped to the canvas. */
    private fun drawRect(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean) {
        val left = minOf(x0, x1); val right = maxOf(x0, x1)
        val top = minOf(y0, y1); val bottom = maxOf(y0, y1)
        for (y in top..bottom) {
            for (x in left..right) {
                if (filled || y == top || y == bottom || x == left || x == right) {
                    plotClipped(x, y)
                }
            }
        }
    }

    // ---------- internals ----------

    private fun parse(code: String): Pair<List<Instruction>, Map<String, Int>>? {
        val program = mutableListOf<Instruction>()
        val labels = mutableMapOf<String, Int>()
        for ((i, raw) in code.lines().withIndex()) {
            val lineNo = i + 1
            var line = raw.substringBefore(';').trim()
            while (line.contains(':')) {
                val label = line.substringBefore(':').trim()
                if (!LABEL_REGEX.matches(label)) {
                    error = "Line $lineNo: invalid label '$label'"; errorLine = lineNo; return null
                }
                labels[label.uppercase()] = program.size
                line = line.substringAfter(':').trim()
            }
            if (line.isEmpty()) continue
            val tokens = line.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            val op = tokens[0].uppercase()
            if (op !in OPCODES) {
                error = "Line $lineNo: unknown instruction '${tokens[0]}'"; errorLine = lineNo; return null
            }
            val expected = when {
                op in FOUR_ARG -> 4
                op in THREE_ARG -> 3
                op in TWO_ARG -> 2
                op in ONE_ARG -> 1
                else -> 0
            }
            val args = tokens.drop(1)
            if (args.size != expected) {
                error = "Line $lineNo: $op expects $expected argument(s), got ${args.size}"
                errorLine = lineNo
                return null
            }
            program.add(Instruction(op, args, lineNo))
        }
        return program to labels
    }

    private fun execute(ins: Instruction): Int? {
        fun fail(msg: String): Int? {
            error = "Line ${ins.lineNo}: $msg"; errorLine = ins.lineNo; return null
        }

        fun resolve(token: String): Int? {
            val reg = token.uppercase()
            if (reg in registers) return registers[reg]
            return token.toIntOrNull()
                ?: fail("'$token' is not a register (A-D) or a number")
        }

        fun reg(token: String): String? {
            val r = token.uppercase()
            if (r in registers) return r
            return fail("unknown register '$token' (use A, B, C or D)")
        }

        fun valueAt(i: Int): Int? = ins.args.getOrNull(i)?.let { resolve(it) }

        fun jumpTarget(): Int? {
            val t = ins.args.getOrNull(0) ?: return fail("${ins.op} needs a label")
            return labels[t.uppercase()] ?: fail("unknown label '$t'")
        }

        fun memOk(addr: Int): Boolean {
            if (addr !in 0 until MEMORY_SIZE) {
                fail("memory address $addr out of range (0..${MEMORY_SIZE - 1})")
                return false
            }
            return true
        }

        fun canvasOk(x: Int, y: Int): Boolean {
            if (x !in 0 until CANVAS_SIZE || y !in 0 until CANVAS_SIZE) {
                fail("canvas position ($x, $y) is off the 16x16 canvas (both must be 0..15)")
                return false
            }
            return true
        }

        fun emitLine(text: String): Int? {
            if (output.size >= MAX_OUTPUT_LINES)
                return fail("output limit of $MAX_OUTPUT_LINES lines exceeded")
            output.add(text)
            return null
        }

        val a = ins.args
        return when (ins.op) {
            "MOV" -> { val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null; registers[r] = v; null }
            "ADD" -> { val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null; registers[r] += v; null }
            "SUB" -> { val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null; registers[r] -= v; null }
            "MUL" -> { val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null; registers[r] *= v; null }
            "DIV" -> {
                val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null
                if (v == 0) return fail("division by zero")
                registers[r] /= v; null
            }
            "MOD" -> {
                val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null
                if (v == 0) return fail("modulo by zero")
                registers[r] %= v; null
            }
            "INC" -> { val r = reg(a[0]) ?: return null; registers[r] += 1; null }
            "DEC" -> { val r = reg(a[0]) ?: return null; registers[r] -= 1; null }
            "NEG" -> { val r = reg(a[0]) ?: return null; registers[r] = -registers.getValue(r); null }
            "NOT" -> { val r = reg(a[0]) ?: return null; registers[r] = registers.getValue(r).inv(); null }
            "AND" -> { val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null; registers[r] = registers.getValue(r) and v; null }
            "OR"  -> { val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null; registers[r] = registers.getValue(r) or v; null }
            "XOR" -> { val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null; registers[r] = registers.getValue(r) xor v; null }
            "SHL" -> { val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null; registers[r] = registers.getValue(r) shl (v and 31); null }
            "SHR" -> { val r = reg(a[0]) ?: return null; val v = valueAt(1) ?: return null; registers[r] = registers.getValue(r) ushr (v and 31); null }
            "LOAD" -> {
                val r = reg(a[0]) ?: return null; val addr = valueAt(1) ?: return null
                if (!memOk(addr)) return null
                registers[r] = memory[addr]; null
            }
            "STORE" -> {
                val addr = valueAt(0) ?: return null; val v = valueAt(1) ?: return null
                if (!memOk(addr)) return null
                memory[addr] = v; null
            }
            "PUSH" -> {
                val r = reg(a[0]) ?: return null
                if (stack.size >= MAX_STACK) return fail("stack overflow (max $MAX_STACK)")
                stack.addLast(registers.getValue(r)); null
            }
            "POP" -> {
                val r = reg(a[0]) ?: return null
                if (stack.isEmpty()) return fail("stack is empty")
                registers[r] = stack.removeLast(); null
            }
            "CMP" -> {
                val x = valueAt(0) ?: return null; val y = valueAt(1) ?: return null
                flagZero = x == y; flagLess = x < y; null
            }
            "JMP" -> jumpTarget()
            "JE", "JZ" -> if (flagZero) jumpTarget() else null
            "JNE", "JNZ" -> if (!flagZero) jumpTarget() else null
            "JG" -> if (!flagZero && !flagLess) jumpTarget() else null
            "JGE" -> if (!flagLess) jumpTarget() else null
            "JL" -> if (flagLess) jumpTarget() else null
            "JLE" -> if (flagZero || flagLess) jumpTarget() else null
            "CALL" -> {
                if (stack.size >= MAX_STACK) return fail("stack overflow (max $MAX_STACK)")
                val t = jumpTarget() ?: return null
                stack.addLast(pc + 1); t
            }
            "RET" -> {
                if (stack.isEmpty()) return fail("RET with empty stack (no matching CALL)")
                val t = stack.removeLast()
                if (t in program.indices) t else { halted = true; null }
            }
            "IN" -> {
                val r = reg(a[0]) ?: return null
                if (inputPos >= inputs.size)
                    return fail("IN has no input left (all ${inputs.size} value(s) already used)")
                registers[r] = inputs[inputPos++]; null
            }
            "OUT" -> { val v = valueAt(0) ?: return null; emitLine(v.toString()) }
            "OUTC" -> {
                val v = valueAt(0) ?: return null
                if (v < 0 || v > 127) return fail("OUTC expects an ASCII code 0-127, got $v")
                if ((output.lastOrNull()?.length ?: 0) >= MAX_LINE_LENGTH)
                    return fail("output line too long (max $MAX_LINE_LENGTH chars)")
                val ch = v.toChar()
                if (output.isEmpty()) emitLine(ch.toString()) else output[output.size - 1] += ch
                null
            }
            "CLR" -> { canvas.fill(0); null }
            "PEN" -> {
                val v = valueAt(0) ?: return null
                if (v !in 0..2) return fail("PEN color must be 0 (erase), 1 (ink) or 2 (accent), got $v")
                pen = v; null
            }
            "PLOT" -> {
                val x = valueAt(0) ?: return null; val y = valueAt(1) ?: return null
                if (!canvasOk(x, y)) return null
                canvas[y * CANVAS_SIZE + x] = pen; null
            }
            "LINE" -> {
                val x0 = valueAt(0) ?: return null; val y0 = valueAt(1) ?: return null
                val x1 = valueAt(2) ?: return null; val y1 = valueAt(3) ?: return null
                drawLine(x0, y0, x1, y1); null
            }
            "RECT" -> {
                val x0 = valueAt(0) ?: return null; val y0 = valueAt(1) ?: return null
                val x1 = valueAt(2) ?: return null; val y1 = valueAt(3) ?: return null
                drawRect(x0, y0, x1, y1, filled = false); null
            }
            "FILL" -> {
                val x0 = valueAt(0) ?: return null; val y0 = valueAt(1) ?: return null
                val x1 = valueAt(2) ?: return null; val y1 = valueAt(3) ?: return null
                drawRect(x0, y0, x1, y1, filled = true); null
            }
            "PIX" -> {
                val r = reg(a[0]) ?: return null
                val x = valueAt(1) ?: return null; val y = valueAt(2) ?: return null
                if (!canvasOk(x, y)) return null
                registers[r] = canvas[y * CANVAS_SIZE + x]; null
            }
            "BEEP" -> {
                val p = valueAt(0) ?: return null
                val d = valueAt(1) ?: return null
                if (p < BEEP_MIN_PITCH || p > BEEP_MAX_PITCH)
                    return fail("BEEP pitch must be $BEEP_MIN_PITCH-$BEEP_MAX_PITCH Hz, got $p")
                if (d < BEEP_MIN_MS || d > BEEP_MAX_MS)
                    return fail("BEEP duration must be $BEEP_MIN_MS-$BEEP_MAX_MS ms, got $d")
                if (beeps.size >= MAX_BEEPS) return fail("too many BEEPs (max $MAX_BEEPS)")
                beeps.add(Beep(p, d)); null
            }
            "NOP" -> null
            "HLT" -> { halted = true; null }
            else -> fail("unknown instruction '${ins.op}'")
        }
    }
}
