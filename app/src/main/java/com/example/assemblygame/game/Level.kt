package com.example.assemblygame.game

/** One BEEP note: pitch in Hz and duration in ms. Used by the VM and as a melody target. */
data class Beep(val pitchHz: Int, val durationMs: Int)

/** One hidden test case: the input values the program receives and the output it must produce. */
data class TestCase(
    val input: List<Int> = emptyList(),
    val expected: List<String>
)

data class Level(
    val id: Int,
    val title: String,
    val task: String,
    val hint: String,
    val starterCode: String,
    val parSteps: Int = 20,
    val bannedSnippets: List<String> = emptyList(),
    val requiredRegex: Regex? = null,
    val requiredMessage: String =
        "Rule broken: you must use a jump instruction (JMP / JE / JNE / JG / JL).",
    val testCases: List<TestCase> = emptyList(),
    val initialCanvas: List<String>? = null,    // picture the canvas starts with
    val expectedCanvas: List<String>? = null,   // picture the player must draw
    val expectedBeeps: List<Beep> = emptyList(),// melody the player must play
    val validate: (vm: AssemblyVM, code: String) -> String? = { _, _ -> null }
) {
    val isGraphics: Boolean get() = initialCanvas != null || expectedCanvas != null
    val isMelody: Boolean get() = expectedBeeps.isNotEmpty()

    fun starsFor(steps: Int): Int = when {
        steps <= parSteps -> 3
        steps <= parSteps * 2 -> 2
        else -> 1
    }
}
