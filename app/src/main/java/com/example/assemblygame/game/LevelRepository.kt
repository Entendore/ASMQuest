package com.example.assemblygame.game

object LevelRepository {

    private val JUMPS = Regex("(?i)\\b(JMP|JE|JZ|JNE|JNZ|JG|JL)\\b")

    // ================= CAMPAIGN 1-22: classics =================

    private val classics: List<Level> = listOf(

        Level(
            id = 1, title = "First Steps", parSteps = 1,
            task = "Store the number 42 in register A.\n\nA currently holds 0. Change it to 42 and press RUN.",
            hint = "MOV A, 42",
            starterCode = "; Task: put 42 in register A\n; Write your code below\n\n"
        ) { vm, _ ->
            if (vm.registers["A"] == 42) null
            else "Register A is ${vm.registers["A"]}, but it should be 42."
        },

        Level(
            id = 2, title = "Math Class", parSteps = 4,
            task = "A = 7 and B = 5 are already set. Store A + B in register C.",
            hint = "MOV C, A\nADD C, B",
            starterCode = "MOV A, 7\nMOV B, 5\n\n; Put A + B into C:\n"
        ) { vm, _ ->
            if (vm.registers["C"] == 12) null
            else "Register C is ${vm.registers["C"]}, but it should be 12."
        },

        Level(
            id = 3, title = "Say It Out Loud", parSteps = 1,
            task = "Print the number 3 to the output panel.\n\nThe OUT instruction writes a value to the output.",
            hint = "OUT 3\n\nor:\n\nMOV A, 3\nOUT A",
            starterCode = "; Print the number 3\n"
        ) { vm, _ ->
            if (vm.output == listOf("3")) null
            else "Output should be exactly one line: 3.\nYour output: ${vm.output.ifEmpty { listOf("(empty)") }.joinToString(", ")}"
        },

        Level(
            id = 4, title = "Swap Shop", parSteps = 5,
            task = "A = 1 and B = 2. Swap their values. Use register C as a temp variable.",
            hint = "MOV C, A\nMOV A, B\nMOV B, C",
            starterCode = "MOV A, 1\nMOV B, 2\n\n; Swap A and B here:\n"
        ) { vm, _ ->
            when {
                vm.registers["A"] != 2 -> "A should be 2, but is ${vm.registers["A"]}."
                vm.registers["B"] != 1 -> "B should be 1, but is ${vm.registers["B"]}."
                else -> null
            }
        },

        Level(
            id = 5, title = "Loop de Loop", parSteps = 24,
            task = "Print the numbers 1 to 5, one per line.\n\nRule: you must use a label and a jump — no writing OUT five times!",
            hint = "MOV A, 1\nloop:\nOUT A\nCMP A, 5\nJE done\nINC A\nJMP loop\ndone:",
            starterCode = "; Print 1..5 using a loop\n",
            requiredRegex = JUMPS
        ) { vm, _ ->
            if (vm.output == (1..5).map { it.toString() }) null
            else "Output should be 1,2,3,4,5 (one per line).\nYour output: ${vm.output.ifEmpty { listOf("(empty)") }.joinToString(", ")}"
        },

        Level(
            id = 6, title = "Sum It Up", parSteps = 60,
            task = "Compute 1 + 2 + ... + 10 and store the result in A.\n\nRules: \"55\" may not appear in your code, and you must use a loop.",
            hint = "MOV A, 0\nMOV B, 1\nloop:\nADD A, B\nINC B\nCMP B, 10\nJG done\nJMP loop\ndone:",
            starterCode = "; Sum 1..10 into A\n",
            bannedSnippets = listOf("55"),
            requiredRegex = JUMPS,
            requiredMessage = "Rule broken: compute the sum with a loop (label + jump)."
        ) { vm, _ ->
            if (vm.registers["A"] == 55) null
            else "A is ${vm.registers["A"]}, but should be 55."
        },

        Level(
            id = 7, title = "Countdown", parSteps = 25,
            task = "Print 5, 4, 3, 2, 1 — one number per line, in that order.\n\nRule: use a loop, not five OUTs.",
            hint = "MOV A, 5\nloop:\nOUT A\nDEC A\nCMP A, 0\nJE done\nJMP loop\ndone:",
            starterCode = "; Countdown from 5 to 1\n",
            requiredRegex = JUMPS,
            requiredMessage = "Rule broken: print the countdown with a loop."
        ) { vm, _ ->
            if (vm.output == (5 downTo 1).map { it.toString() }) null
            else "Output should be 5,4,3,2,1.\nYour output: ${vm.output.ifEmpty { listOf("(empty)") }.joinToString(", ")}"
        },

        Level(
            id = 8, title = "Double Trouble", parSteps = 2,
            task = "A starts at 25. Double it so A becomes 50.\n\nRule: \"50\" may not appear in your code.",
            hint = "A register can be added to itself:\n\nADD A, A",
            starterCode = "MOV A, 25\n\n; Double A:\n",
            bannedSnippets = listOf("50")
        ) { vm, _ ->
            if (vm.registers["A"] == 50) null
            else "A is ${vm.registers["A"]}, but should be 50."
        },

        Level(
            id = 9, title = "Times Table", parSteps = 20,
            task = "Compute 3 × 4 using repeated addition and store it in A.\n\nRules: \"12\" may not appear, and no MUL — add in a loop!",
            hint = "MOV A, 0\nMOV B, 0\nMOV C, 4\nloop:\nADD A, 3\nINC B\nCMP B, C\nJL loop",
            starterCode = "; Multiply 3 by 4 using a loop\n",
            bannedSnippets = listOf("12"),
            requiredRegex = Regex("(?i)\\bADD\\b"),
            requiredMessage = "Rule broken: multiply using repeated ADD, not MUL."
        ) { vm, _ ->
            if (vm.registers["A"] == 12) null
            else "A is ${vm.registers["A"]}, but should be 12."
        },

        Level(
            id = 10, title = "Even Stevens", parSteps = 25,
            task = "Print the even numbers 0, 2, 4, 6, 8 — one per line.\n\nRule: use a loop.",
            hint = "MOV A, 0\nloop:\nOUT A\nADD A, 2\nCMP A, 8\nJG done\nJMP loop\ndone:",
            starterCode = "; Print even numbers 0..8\n",
            requiredRegex = JUMPS,
            requiredMessage = "Rule broken: print them with a loop."
        ) { vm, _ ->
            if (vm.output == listOf("0", "2", "4", "6", "8")) null
            else "Output should be 0,2,4,6,8.\nYour output: ${vm.output.ifEmpty { listOf("(empty)") }.joinToString(", ")}"
        },

        Level(
            id = 11, title = "Big Multiply", parSteps = 3,
            task = "A = 7 and B = 6. Set A = A × B using the MUL instruction.\n\nRule: \"42\" may not appear in your code.",
            hint = "MUL A, B",
            starterCode = "MOV A, 7\nMOV B, 6\n\n; Multiply A by B:\n",
            bannedSnippets = listOf("42")
        ) { vm, _ ->
            if (vm.registers["A"] == 42) null
            else "A is ${vm.registers["A"]}, but should be 42."
        },

        Level(
            id = 12, title = "Divide & Conquer", parSteps = 8,
            task = "A = 17 and B = 5. Print 17 ÷ 5 (quotient), then the remainder — two lines.\n\nUse DIV and MOD. Careful: DIV destroys A, so copy it first!",
            hint = "MOV C, A\nMOV D, A\nDIV C, B\nOUT C\nMOD D, B\nOUT D",
            starterCode = "MOV A, 17\nMOV B, 5\n\n; Print the quotient, then the remainder:\n"
        ) { vm, code ->
            when {
                !Regex("(?i)\\bMOD\\b").containsMatchIn(code) ->
                    "You must use MOD to get the remainder."
                vm.output != listOf("3", "2") ->
                    "Output should be two lines: 3 then 2.\nYour output: ${vm.output.ifEmpty { listOf("(empty)") }.joinToString(", ")}"
                else -> null
            }
        },

        Level(
            id = 13, title = "Memory Lane", parSteps = 4,
            task = "Memory has 64 slots. A = 123 already.\n\n1) STORE A into memory slot 10\n2) Set A to 0\n3) LOAD slot 10 back into A\n\nA must be 123 again when you finish.",
            hint = "STORE 10, A\nMOV A, 0\nLOAD A, 10",
            starterCode = "MOV A, 123\n\n; 1) STORE A into slot 10\n; 2) set A to 0\n; 3) LOAD slot 10 back into A\n"
        ) { vm, code ->
            when {
                !Regex("(?i)\\bSTORE\\b").containsMatchIn(code) -> "You must use STORE to write to memory."
                !Regex("(?i)\\bLOAD\\b").containsMatchIn(code) -> "You must use LOAD to read it back."
                vm.memory[10] != 123 -> "Memory slot 10 is ${vm.memory[10]}, but should be 123."
                vm.registers["A"] != 123 -> "A is ${vm.registers["A"]}, but should be 123 after the LOAD."
                else -> null
            }
        },

        Level(
            id = 14, title = "Function Call", parSteps = 8,
            task = "A = 21. The program already CALLs a subroutine named \"double\" twice.\n\nFill in the body of double so it doubles A. A must be 84 at the end.\n\n(HLT is there so we don't fall into the subroutine by accident.)",
            hint = "double:\n  ADD A, A\n  RET",
            starterCode = "; Task: double A twice using a subroutine\nMOV A, 21\n\nCALL double\nCALL double\nHLT\n\ndouble:\n  ; double the value in A here\n  RET\n",
            bannedSnippets = listOf("84")
        ) { vm, _ ->
            if (vm.registers["A"] == 84) null
            else "A is ${vm.registers["A"]}, but should be 84 after two calls."
        },

        // ---------- input-driven levels (can't be hardcoded!) ----------

        Level(
            id = 15, title = "Input Echo", parSteps = 2,
            task = "New instruction: IN reads the next input value into a register.\n\nRead one number and print it back.\n\nYour code must work for ALL test cases — the input changes every run!",
            hint = "IN A\nOUT A",
            starterCode = "; Read a number and print it\n",
            testCases = listOf(
                TestCase(listOf(5), listOf("5")),
                TestCase(listOf(42), listOf("42")),
                TestCase(listOf(-7), listOf("-7"))
            )
        ),

        Level(
            id = 16, title = "Sum Machine", parSteps = 4,
            task = "Read TWO numbers and print their sum.\n\nEach IN call takes the next number from the input queue.",
            hint = "IN A\nIN B\nADD A, B\nOUT A",
            starterCode = "; Read two numbers, print their sum\n",
            testCases = listOf(
                TestCase(listOf(3, 4), listOf("7")),
                TestCase(listOf(10, -2), listOf("8")),
                TestCase(listOf(100, 231), listOf("331"))
            )
        ),

        Level(
            id = 17, title = "Make It Positive", parSteps = 6,
            task = "Read a number and print its absolute value.\n\nNEG r flips the sign. CMP + JG can decide which path to take.",
            hint = "IN A\nCMP A, 0\nJG done\nNEG A\ndone:\nOUT A",
            starterCode = "; Print |n|\n",
            testCases = listOf(
                TestCase(listOf(-5), listOf("5")),
                TestCase(listOf(5), listOf("5")),
                TestCase(listOf(0), listOf("0"))
            )
        ),

        Level(
            id = 18, title = "Bigger Number", parSteps = 6,
            task = "Read two numbers and print the larger one. If they're equal, either is fine.",
            hint = "IN A\nIN B\nCMP A, B\nJG a_big\nOUT B\nJMP done\na_big:\nOUT A\ndone:",
            starterCode = "; Print the max of the two inputs\n",
            testCases = listOf(
                TestCase(listOf(3, 9), listOf("9")),
                TestCase(listOf(10, 2), listOf("10")),
                TestCase(listOf(4, 4), listOf("4"))
            )
        ),

        Level(
            id = 19, title = "Say OK", parSteps = 2,
            task = "Print the word OK — two characters on one line.\n\nOUTC prints the character with the given ASCII code. 'O' is 79, 'K' is 75. OUTC appends to the current line; OUT always starts a new one.",
            hint = "OUTC 79\nOUTC 75",
            starterCode = "; Print the word OK, character by character\n",
            requiredRegex = Regex("(?i)\\bOUTC\\b"),
            requiredMessage = "Rule broken: build the word with OUTC, not OUT.",
            testCases = listOf(TestCase(expected = listOf("OK")))
        ),

        Level(
            id = 20, title = "Alphabet Soup", parSteps = 21,
            task = "Print ABCDE on one line, using a loop over ASCII codes (A = 65 … E = 69).",
            hint = "MOV A, 65\nloop:\nOUTC A\nINC A\nCMP A, 70\nJL loop",
            starterCode = "; Print A..E with a loop\n",
            requiredRegex = JUMPS,
            requiredMessage = "Rule broken: generate the letters with a loop.",
            testCases = listOf(TestCase(expected = listOf("ABCDE")))
        ),

        Level(
            id = 21, title = "Digit Sum", parSteps = 22,
            task = "Read a number and print the sum of its digits.\n\nExample: 123 → 1 + 2 + 3 = 6.\n\nMOD x, 10 gives the last digit; DIV x, 10 chops it off. Loop until A is 0.",
            hint = "IN A\nMOV B, 0\nloop:\nMOV C, A\nMOD C, 10\nADD B, C\nDIV A, 10\nCMP A, 0\nJG loop\nOUT B",
            starterCode = "; Sum the digits of the input\n",
            requiredRegex = JUMPS,
            requiredMessage = "Rule broken: use a loop with DIV and MOD.",
            testCases = listOf(
                TestCase(listOf(123), listOf("6")),
                TestCase(listOf(9999), listOf("36")),
                TestCase(listOf(7), listOf("7"))
            )
        ),

        Level(
            id = 22, title = "Fibonacci", parSteps = 52,
            task = "Print the first 6 Fibonacci numbers, one per line: 1 1 2 3 5 8.\n\nKeep the current and previous values in two registers, use a third as a temp and a fourth as a counter.",
            hint = "MOV A, 1\nMOV B, 0\nMOV D, 0\nloop:\nOUT A\nMOV C, A\nADD C, B\nMOV B, A\nMOV A, C\nINC D\nCMP D, 6\nJL loop\nHLT",
            starterCode = "; Print the first 6 Fibonacci numbers\n",
            requiredRegex = JUMPS,
            requiredMessage = "Rule broken: generate the sequence with a loop.",
            testCases = listOf(TestCase(expected = listOf("1", "1", "2", "3", "5", "8")))
        )
    )

    // ================= helpers to build target pictures =================

    private const val G = AssemblyVM.CANVAS_SIZE

    private fun full(pixel: (x: Int, y: Int) -> Char): List<String> {
        val g = Array(G) { CharArray(G) { '.' } }
        for (y in 0 until G) for (x in 0 until G) g[y][x] = pixel(x, y)
        return g.map { it.concatToString() }
    }

    // ================= CAMPAIGN 23-45: graphics, text, data, melodies =================

    private val extras: List<Level> = listOf(

        // ----- graphics: drawing -----

        Level(
            id = 23, title = "First Pixel", parSteps = 1,
            task = "The canvas is 16×16. x grows to the right, y grows downward.\n\nDraw a single ink pixel at (8, 8) — the center.\n\nWatch the PIXELS tab while you run!",
            hint = "PLOT 8, 8",
            starterCode = "; Draw one pixel at x=8, y=8\n",
            expectedCanvas = full { x, y -> if (x == 8 && y == 8) '#' else '.' }
        ),

        Level(
            id = 24, title = "Horizon", parSteps = 70,
            task = "Draw a horizontal line all the way across the canvas at y = 8.\n\nOne PLOT per pixel — use a loop over x!",
            hint = "MOV A, 0\nloop:\nPLOT A, 8\nINC A\nCMP A, 16\nJL loop",
            starterCode = "; Draw a line from (0,8) to (15,8)\n",
            expectedCanvas = full { _, y -> if (y == 8) '#' else '.' }
        ),

        Level(
            id = 25, title = "Skyline", parSteps = 70,
            task = "Draw a vertical line from top to bottom at x = 8.",
            hint = "MOV A, 0\nloop:\nPLOT 8, A\nINC A\nCMP A, 16\nJL loop",
            starterCode = "; Draw a line from (8,0) to (8,15)\n",
            expectedCanvas = full { x, _ -> if (x == 8) '#' else '.' }
        ),

        Level(
            id = 26, title = "Diagonal", parSteps = 70,
            task = "Draw the main diagonal: one pixel for every (x, y) where x equals y.",
            hint = "MOV A, 0\nloop:\nPLOT A, A\nINC A\nCMP A, 16\nJL loop",
            starterCode = "; Draw the diagonal from (0,0) to (15,15)\n",
            expectedCanvas = full { x, y -> if (x == y) '#' else '.' }
        ),

        Level(
            id = 27, title = "Boxed In", parSteps = 95,
            task = "Draw the border of an 8×8 square from (4,4) to (11,11) — only the outline, hollow inside.\n\nTip: two loops. One draws the top and bottom rows, the other the left and right columns.",
            hint = "MOV A, 4\ntop:\nPLOT A, 4\nPLOT A, 11\nINC A\nCMP A, 12\nJL top\nMOV A, 4\nside:\nPLOT 4, A\nPLOT 11, A\nINC A\nCMP A, 12\nJL side",
            starterCode = "; Outline the square (4,4)..(11,11)\n",
            expectedCanvas = full { x, y ->
                if (x in 4..11 && y in 4..11 && (x == 4 || x == 11 || y == 4 || y == 11)) '#' else '.'
            }
        ),

        Level(
            id = 28, title = "Checkerboard", parSteps = 2900,
            task = "Fill the whole canvas like a chessboard:\n\n• ink (#) where (x + y) is even\n• accent (+) where (x + y) is odd\n\nYou'll need two colors: PEN 1 is ink, PEN 2 is accent. AND with 1 tests even/odd.",
            hint = "MOV C, 0\nrow:\nMOV A, 0\ncol:\nMOV B, A\nADD B, C\nAND B, 1\nCMP B, 1\nJE odd\nPEN 1\nJMP put\nodd:\nPEN 2\nput:\nPLOT A, C\nINC A\nCMP A, 16\nJL col\nINC C\nCMP C, 16\nJL row",
            starterCode = "; Two-color checkerboard\n",
            expectedCanvas = full { x, y -> if ((x + y) % 2 == 0) '#' else '+' }
        ),

        Level(
            id = 29, title = "Ramp Up", parSteps = 780,
            task = "Draw a filled lower-left triangle: plot every pixel where x ≤ y.\n\nRow 0 gets 1 pixel, row 1 gets 2 … row 15 gets 16. The inner loop's limit is a register!",
            hint = "MOV C, 0\nrow:\nMOV A, 0\ncol:\nPLOT A, C\nINC A\nCMP A, C\nJG endrow\nJMP col\nendrow:\nINC C\nCMP C, 16\nJL row",
            starterCode = "; Fill the triangle x <= y\n",
            expectedCanvas = full { x, y -> if (x <= y) '#' else '.' }
        ),

        Level(
            id = 30, title = "Negative Space", parSteps = 2400,
            task = "The canvas starts covered in a pattern — but inverted!\n\nErase every ink pixel and draw ink where the canvas is empty. PIX reads a pixel, PEN 0 erases.",
            hint = "MOV C, 0\nrow:\nMOV A, 0\ncol:\nPIX B, A, C\nCMP B, 0\nJE on\nPEN 0\nJMP put\non:\nPEN 1\nput:\nPLOT A, C\nINC A\nCMP A, 16\nJL col\nINC C\nCMP C, 16\nJL row",
            starterCode = "; Invert the starting pattern\n",
            initialCanvas = full { x, y -> if ((x + y) % 2 != 0) '#' else '.' },
            expectedCanvas = full { x, y -> if ((x + y) % 2 == 0) '#' else '.' }
        ),

        Level(
            id = 31, title = "Mirror Mirror", parSteps = 1150,
            task = "The left half of the canvas has a shape. Copy it mirrored to the right half:\n\nevery pixel at (x, y) must also appear at (15 − x, y).\n\nPIX reads pixels; PEN can take a register: PEN B sets the pen from B.",
            hint = "MOV C, 8\nrow:\nMOV A, 0\ncol:\nPIX B, A, C\nMOV D, 15\nSUB D, A\nPEN B\nPLOT D, C\nINC A\nCMP A, 8\nJL col\nINC C\nCMP C, 16\nJL row",
            starterCode = "; Mirror the left half to the right\n",
            initialCanvas = full { x, y -> if (x <= 7 && y >= 8 && x <= y - 8) '#' else '.' },
            expectedCanvas = full { x, y ->
                val mx = minOf(x, 15 - x)
                if (y >= 8 && mx <= y - 8) '#' else '.'
            }
        ),

        Level(
            id = 32, title = "Orbit", parSteps = 3000,
            task = "Draw a filled circle: every pixel whose squared distance from the center (8,8) is at most 36.\n\nThat is: (x−8)² + (y−8)² ≤ 36. MUL a register by itself to square it.",
            hint = "MOV C, 0\nrow:\nMOV A, 0\ncol:\nMOV B, A\nSUB B, 8\nMUL B, B\nMOV D, C\nSUB D, 8\nMUL D, D\nADD B, D\nCMP B, 36\nJG skip\nPLOT A, C\nskip:\nINC A\nCMP A, 16\nJL col\nINC C\nCMP C, 16\nJL row",
            starterCode = "; Draw the filled circle around (8,8)\n",
            expectedCanvas = full { x, y ->
                val dx = x - 8; val dy = y - 8
                if (dx * dx + dy * dy <= 36) '#' else '.'
            }
        ),

        // ----- text task -----

        Level(
            id = 33, title = "Next Letter", parSteps = 6,
            task = "Read a letter as its ASCII code (e.g. 67 = 'C') and print the letter that comes after it.\n\nTwist: the input might be 'Z' (90) — then print 'A' (65). 'A' is 65 … 'Z' is 90.",
            hint = "IN A\nINC A\nCMP A, 91\nJL out\nMOV A, 65\nout:\nOUTC A",
            starterCode = "; Print the next letter, wrapping Z -> A\n",
            testCases = listOf(
                TestCase(listOf(67), listOf("D")),
                TestCase(listOf(90), listOf("A")),
                TestCase(listOf(77), listOf("N"))
            )
        ),

        // ----- data tasks -----

        Level(
            id = 34, title = "Sort Three", parSteps = 30,
            task = "Read three numbers and store them in memory slots 0, 1, 2 in ascending order.\n\nCMP + a temp register swaps two values. Mind every ordering the input could have!",
            hint = "IN A\nIN B\nIN C\nCMP B, A\nJG s2\nMOV D, A\nMOV A, B\nMOV B, D\ns2:\nCMP C, B\nJG s3\nMOV D, B\nMOV B, C\nMOV C, D\nCMP B, A\nJG s3\nMOV D, A\nMOV A, B\nMOV B, D\ns3:\nSTORE 0, A\nSTORE 1, B\nSTORE 2, C",
            starterCode = "; Read 3 numbers, store them sorted in memory 0..2\n",
            testCases = listOf(
                TestCase(listOf(3, 1, 2), listOf("1", "2", "3")),
                TestCase(listOf(5, 5, -2), listOf("-2", "5", "5")),
                TestCase(listOf(9, 4, 7), listOf("4", "7", "9"))
            )
        ) { vm, _ ->
            val expected = vm.inputs.sorted()
            val got = listOf(vm.memory[0], vm.memory[1], vm.memory[2])
            if (got == expected) null
            else "Memory 0..2 is $got but should be $expected."
        },

        Level(
            id = 35, title = "Head Count", parSteps = 45,
            task = "The first input is N. Then come N more values.\n\nPrint how many of those N values are strictly greater than zero.",
            hint = "IN A\nMOV B, 0\nMOV C, 0\nloop:\nCMP C, A\nJG done\nIN D\nCMP D, 0\nJG pos\nJMP next\npos:\nINC B\nnext:\nINC C\nJMP loop\ndone:\nOUT B",
            starterCode = "; Count the positive values\n",
            testCases = listOf(
                TestCase(listOf(3, -1, 5, 0), listOf("1")),
                TestCase(listOf(4, 1, 2, 3, 4), listOf("4")),
                TestCase(listOf(2, -5, -6), listOf("0"))
            )
        ),

        Level(
            id = 36, title = "Rewind", parSteps = 60,
            task = "The first input is N. Then come N values.\n\nPrint all N values in REVERSE order, one per line.\n\nSTORE them in memory as they arrive, then walk the slots backwards with LOAD.",
            hint = "IN A\nMOV B, 0\nread:\nCMP B, A\nJG done_read\nIN C\nSTORE B, C\nINC B\nJMP read\ndone_read:\nDEC B\nprint:\nCMP B, 0\nJL done\nLOAD C, B\nOUT C\nDEC B\nJMP print\ndone:",
            starterCode = "; Echo the inputs in reverse\n",
            testCases = listOf(
                TestCase(listOf(3, 7, 8, 9), listOf("9", "8", "7")),
                TestCase(listOf(1, 42), listOf("42")),
                TestCase(listOf(4, 1, 2, 3, 4), listOf("4", "3", "2", "1"))
            )
        ),

        Level(
            id = 37, title = "Rooftop", parSteps = 4,
            task = "New instruction: LINE x0, y0, x1, y1 draws a straight line (clipped to the canvas).\n\nDraw the rooftop with exactly 4 LINE calls:\n• ground: (0,12) to (15,12)\n• left wall: (4,12) to (4,4)\n• roof: (4,4) to (11,4)\n• right wall: (11,4) to (11,12)\n\nRule: PLOT is banned — use LINE!",
            hint = "LINE 0, 12, 15, 12\nLINE 4, 12, 4, 4\nLINE 4, 4, 11, 4\nLINE 11, 4, 11, 12",
            starterCode = "; Draw the rooftop with LINE\n",
            bannedSnippets = listOf("PLOT"),
            requiredRegex = Regex("(?i)\\bLINE\\b"),
            requiredMessage = "Rule broken: draw the shapes with LINE.",
            expectedCanvas = full { x, y ->
                if (y == 12 || (x == 4 && y in 4..12) ||
                    (y == 4 && x in 4..11) || (x == 11 && y in 4..12)) '#' else '.'
            }
        ),

        Level(
            id = 38, title = "Less Or Equal", parSteps = 24,
            task = "New jumps: JGE jumps when the first CMP value was ≥ the second; JLE jumps when it was ≤.\n\nRead a number N and print 1, 2, … N — one per line — using JLE for the loop exit.",
            hint = "IN A\nMOV B, 0\nloop:\nINC B\nOUT B\nCMP B, A\nJLE loop",
            starterCode = "; Print 1..N using JLE\n",
            requiredRegex = Regex("(?i)\\bJLE\\b"),
            requiredMessage = "Rule broken: exit the loop with JLE.",
            testCases = listOf(
                TestCase(listOf(5), listOf("1", "2", "3", "4", "5")),
                TestCase(listOf(1), listOf("1")),
                TestCase(listOf(3), listOf("1", "2", "3"))
            )
        ),

        Level(
            id = 39, title = "Picture Frame", parSteps = 4,
            task = "New instructions: RECT draws a rectangle outline, FILL draws a solid one — both clipped to the canvas.\n\nDraw a 2-pixel-thick frame around the whole canvas.\n\nRules: PLOT and LINE are banned!",
            hint = "FILL 0, 0, 15, 1\nFILL 0, 14, 15, 15\nFILL 0, 0, 1, 15\nFILL 14, 0, 15, 15",
            starterCode = "; Draw a 2px frame with FILL\n",
            bannedSnippets = listOf("PLOT", "LINE"),
            requiredRegex = Regex("(?i)\\b(RECT|FILL)\\b"),
            requiredMessage = "Rule broken: draw the frame with RECT or FILL.",
            expectedCanvas = full { x, y -> if (x <= 1 || x >= 14 || y <= 1 || y >= 14) '#' else '.' }
        ),

        Level(
            id = 40, title = "Towers", parSteps = 3,
            task = "Build a skyline of three solid towers:\n\n• left: (0,8) to (4,15)\n• middle: (6,4) to (10,15)\n• right: (12,10) to (15,15)\n\nRule: no PLOT — only FILL.",
            hint = "FILL 0, 8, 4, 15\nFILL 6, 4, 10, 15\nFILL 12, 10, 15, 15",
            starterCode = "; Three towers with FILL\n",
            bannedSnippets = listOf("PLOT"),
            requiredRegex = Regex("(?i)\\bFILL\\b"),
            requiredMessage = "Rule broken: build the towers with FILL.",
            expectedCanvas = full { x, y ->
                if ((x in 0..4 && y >= 8) || (x in 6..10 && y >= 4) || (x in 12..15 && y >= 10)) '#' else '.'
            }
        ),

        Level(
            id = 41, title = "Concentric", parSteps = 85,
            task = "Draw 8 nested square outlines with one loop:\n\nRECT k, k, 15−k, 15−k for k = 0…7, alternating colors — ink for even k, accent for odd k.",
            hint = "MOV A, 0\nloop:\nMOV B, A\nAND B, 1\nCMP B, 1\nJE odd\nPEN 1\nJMP d\nodd:\nPEN 2\nd:\nMOV D, 15\nSUB D, A\nRECT A, A, D, D\nINC A\nCMP A, 8\nJL loop",
            starterCode = "; Nested squares, alternating colors\n",
            requiredRegex = Regex("(?i)\\bRECT\\b"),
            requiredMessage = "Rule broken: draw the rings with RECT.",
            expectedCanvas = full { x, y ->
                val r = minOf(x, y, 15 - x, 15 - y)
                if (r % 2 == 0) '#' else '+'
            }
        ),

        Level(
            id = 42, title = "Four Panes", parSteps = 3,
            task = "Draw a window:\n\n• square outline from (2,2) to (13,13)\n• vertical line x=8 between the top and bottom of the square\n• horizontal line y=8 across it\n\nRule: use RECT for the outline.",
            hint = "RECT 2, 2, 13, 13\nLINE 8, 2, 8, 13\nLINE 2, 8, 13, 8",
            starterCode = "; A window with four panes\n",
            requiredRegex = Regex("(?i)\\bRECT\\b"),
            requiredMessage = "Rule broken: draw the outline with RECT.",
            expectedCanvas = full { x, y ->
                val border = ((x == 2 || x == 13) && y in 2..13) || ((y == 2 || y == 13) && x in 2..13)
                val cross = (x == 8 && y in 2..13) || (y == 8 && x in 2..13)
                if (border || cross) '#' else '.'
            }
        ),

        // ----- melody levels -----

        Level(
            id = 43, title = "Do Re Mi", parSteps = 3,
            task = "New instruction: BEEP pitch, ms plays a tone — BEEP 440, 300 plays 440 Hz for 300 ms.\n\nPlay three notes, 300 ms each:\n• 523 Hz (C)\n• 659 Hz (E)\n• 784 Hz (G)\n\nNotes are judged by exact pitch and duration. Press RUN to hear your melody!",
            hint = "BEEP 523, 300\nBEEP 659, 300\nBEEP 784, 300",
            starterCode = "; Play C, E, G — 300 ms each\n",
            expectedBeeps = listOf(Beep(523, 300), Beep(659, 300), Beep(784, 300))
        ),

        Level(
            id = 44, title = "Scale Ladder", parSteps = 25,
            task = "Play a rising scale with a loop: six notes at 400, 450, 500, 550, 600, 650 Hz — each 200 ms.\n\nThe pitch is just a register: BEEP it, then ADD 50. Stop before 700.",
            hint = "MOV A, 400\nloop:\nBEEP A, 200\nADD A, 50\nCMP A, 700\nJL loop",
            starterCode = "; Rising scale, 400 Hz to 650 Hz\n",
            requiredRegex = JUMPS,
            requiredMessage = "Rule broken: climb the scale with a loop.",
            expectedBeeps = (400..650 step 50).map { Beep(it, 200) }
        ),

        Level(
            id = 45, title = "Morse Heart", parSteps = 26,
            task = "Play the rhythm long–short–short–long: four notes at 440 Hz with durations 500, 150, 150, 500 ms.\n\nThe durations are DATA: STORE them into memory slots 20–23 first, then loop with LOAD — BEEP A, B where B comes from memory.",
            hint = "STORE 20, 500\nSTORE 21, 150\nSTORE 22, 150\nSTORE 23, 500\nMOV A, 440\nMOV C, 20\nloop:\nLOAD B, C\nBEEP A, B\nINC C\nCMP C, 24\nJL loop",
            starterCode = "; Long-short-short-long at 440 Hz\n",
            requiredRegex = JUMPS,
            requiredMessage = "Rule broken: read the durations from memory with LOAD in a loop.",
            expectedBeeps = listOf(
                Beep(440, 500), Beep(440, 150), Beep(440, 150), Beep(440, 500)
            )
        )
    )

    val levels: List<Level> = classics + extras

    /** Free-play level. Id 0 is reserved for it; nothing here is scored or recorded. */
    val sandbox = Level(
        id = 0, title = "Sandbox", parSteps = 1,
        task = "Free play with every instruction — nothing is scored, nothing breaks.\n\n" +
            "• Tap DEMOS for example programs\n• Long-press PLAY to change playback speed\n" +
            "• Long-press a register chip to switch DEC / HEX / BIN\n" +
            "• Open the PIXELS tab and watch your code draw",
        hint = "(explore!)",
        starterCode = "; Free play — try the DEMOS button, then PLAY\nCLR\nPEN 2\nLINE 0, 0, 15, 15\n"
    )
}
