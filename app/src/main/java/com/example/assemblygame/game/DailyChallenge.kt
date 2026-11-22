package com.example.assemblygame.game

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.abs

/**
 * One deterministic puzzle per calendar day (UTC), generated from the date alone.
 * The same day produces the same level on every device — no server, no download.
 */
object DailyChallenge {

    private const val DAY_MS = 86_400_000L

    /** Days since 1970-01-01 UTC — changes exactly once per day. */
    fun todayId(): Long = System.currentTimeMillis() / DAY_MS

    fun dateLabel(dayId: Long): String =
        SimpleDateFormat("MMM d", Locale.US).format(Date(dayId * DAY_MS))

    fun levelForDay(dayId: Long): Level {
        val rnd = Random(dayId * 2654435761L)
        val base = when (rnd.nextInt(6)) {
            0 -> sumMachine(rnd)
            1 -> maxOfThree(rnd)
            2 -> doubleIt(rnd)
            3 -> digitSum(rnd)
            4 -> countdown(rnd)
            else -> absoluteSum(rnd)
        }
        return base.copy(
            id = -dayId.toInt(),                       // negative ids never clash with built-ins/customs
            title = "Daily ${dateLabel(dayId)} — ${base.title}"
        )
    }

    private fun sumMachine(rnd: Random): Level {
        val n = 2 + rnd.nextInt(2)
        val tests = List(3) {
            val xs = List(n) { rnd.nextInt(61) - 30 }
            TestCase(xs, listOf(xs.sum().toString()))
        }
        return Level(
            id = 0, title = "Sum Machine", parSteps = 4 * n + 2,
            task = "Read $n numbers with IN and print their sum.\n\n" +
                "Example: input [${tests[0].input.joinToString(", ")}] → output ${tests[0].expected[0]}.\n\n" +
                "The input is different for every test case!",
            hint = "IN A\nIN B\nADD A, B\nOUT A   ; add more INs/registers for more inputs",
            starterCode = "; Read $n numbers and print their sum\n",
            testCases = tests
        )
    }

    private fun maxOfThree(rnd: Random): Level {
        val tests = List(3) {
            val xs = List(3) { rnd.nextInt(101) - 50 }
            TestCase(xs, listOf(xs.max().toString()))
        }
        return Level(
            id = 0, title = "Crown the Biggest", parSteps = 12,
            task = "Read 3 numbers and print the largest one.\n\n" +
                "Example: input [${tests[0].input.joinToString(", ")}] → output ${tests[0].expected[0]}.",
            hint = "IN A\nIN B\nCMP A, B\nJG keep\nMOV A, B\nkeep:\nIN B\nCMP A, B\nJG done\nMOV A, B\ndone:\nOUT A",
            starterCode = "; Read 3 numbers, print the biggest\n",
            testCases = tests
        )
    }

    private fun doubleIt(rnd: Random): Level {
        val banMul = rnd.nextBoolean()
        val tests = List(3) {
            val x = rnd.nextInt(101) - 50
            TestCase(listOf(x), listOf((2 * x).toString()))
        }
        return Level(
            id = 0, title = "Twice as Much", parSteps = 3,
            task = "Read a number and print it times 2.\n\n" +
                "Example: input [${tests[0].input[0]}] → output ${tests[0].expected[0]}." +
                if (banMul) "\n\nToday's twist: MUL is banned — double it with ADD!" else "",
            hint = "IN A\nADD A, A\nOUT A",
            starterCode = "; Read a number, print double\n",
            bannedSnippets = if (banMul) listOf("MUL") else emptyList(),
            testCases = tests
        )
    }

    private fun digitSum(rnd: Random): Level {
        val tests = List(3) {
            val x = 100 + rnd.nextInt(9900)
            TestCase(listOf(x), listOf(digitSumOf(x).toString()))
        }
        return Level(
            id = 0, title = "Digit Digester", parSteps = 22,
            task = "Read a number and print the sum of its digits.\n\n" +
                "Example: input [${tests[0].input[0]}] → output ${tests[0].expected[0]}.\n\n" +
                "MOD x, 10 gives the last digit; DIV x, 10 chops it off. Loop until A is 0.",
            hint = "IN A\nMOV B, 0\nloop:\nMOV C, A\nMOD C, 10\nADD B, C\nDIV A, 10\nCMP A, 0\nJG loop\nOUT B",
            starterCode = "; Sum the digits of the input\n",
            testCases = tests
        )
    }

    private fun digitSumOf(x: Int): Int {
        var v = x; var s = 0
        while (v > 0) { s += v % 10; v /= 10 }
        return s
    }

    private fun countdown(rnd: Random): Level {
        val tests = List(3) {
            val n = 3 + rnd.nextInt(8)
            TestCase(listOf(n), (n downTo 1).map { it.toString() })
        }
        return Level(
            id = 0, title = "Blast Off", parSteps = 45,
            task = "Read a number N and print a countdown from N down to 1, one per line.\n\n" +
                "Example: input [${tests[0].input[0]}] → output ${tests[0].expected.joinToString(", ")}.",
            hint = "IN A\nloop:\nOUT A\nDEC A\nCMP A, 0\nJE done\nJMP loop\ndone:",
            starterCode = "; Read N, print N..1\n",
            testCases = tests
        )
    }

    private fun absoluteSum(rnd: Random): Level {
        val tests = List(3) {
            val a = rnd.nextInt(81) - 40
            val b = rnd.nextInt(81) - 40
            TestCase(listOf(a, b), listOf(abs(a + b).toString()))
        }
        return Level(
            id = 0, title = "Positive Vibes", parSteps = 7,
            task = "Read 2 numbers and print the absolute value of their sum.\n\n" +
                "Example: input [${tests[0].input.joinToString(", ")}] → output ${tests[0].expected[0]}.",
            hint = "IN A\nIN B\nADD A, B\nCMP A, 0\nJG done\nNEG A\ndone:\nOUT A",
            starterCode = "; Read 2 numbers, print |a + b|\n",
            testCases = tests
        )
    }
}
