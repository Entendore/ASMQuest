package com.example.assemblygame.game

import android.content.Context

object Achievements {

    data class Achievement(val id: String, val title: String, val desc: String)

    val ALL = listOf(
        Achievement("first_win", "Hello, Registers", "Complete your first level"),
        Achievement("five_wins", "Getting Somewhere", "Complete 5 levels"),
        Achievement("ten_wins", "Half Way There", "Complete 10 levels"),
        Achievement("all_wins", "Machine Master", "Complete every level"),
        Achievement("stars_15", "Star Collector", "Earn 15 stars"),
        Achievement("stars_40", "Constellation", "Earn 40 stars"),
        Achievement("perfect_5", "Perfectionist", "Get 3★ on 5 levels"),
        Achievement("no_hint_5", "Purist", "Complete 5 levels without hints"),
        Achievement("debugger", "Step by Step", "Pass a level after using the debugger"),
        Achievement("polyglot", "Polyglot", "Use 8+ different instructions in one winning program"),
        Achievement("pixel_artist", "Pixel Artist", "Complete a graphics level"),
        Achievement("architect", "Architect", "Solve a level using RECT or FILL"),
        Achievement("melody", "Ear for Music", "Complete a melody level")
    )

    /** Called after a win. Unlocks anything newly earned; returns the fresh ones (for toasts). */
    fun checkAfterWin(
        context: Context,
        stars: Int,
        usedHint: Boolean,
        usedDebugger: Boolean,
        distinctOpcodes: Int,
        graphicsLevel: Boolean,
        usedRectFill: Boolean,
        melodyLevel: Boolean
    ): List<Achievement> {
        val newly = mutableListOf<Achievement>()
        val completed = ProgressStore.completedCount(context)
        val total = LevelRepository.levels.size
        val totalStars = ProgressStore.totalStars(context)
        val perfect = LevelRepository.levels.count { ProgressStore.stars(context, it.id) == 3 }
        val noHint = ProgressStore.noHintCompletedCount(context)

        fun unlockIf(id: String, condition: Boolean) {
            val a = ALL.first { it.id == id }
            if (condition && ProgressStore.unlockAchievement(context, id)) newly.add(a)
        }

        unlockIf("first_win", completed >= 1)
        unlockIf("five_wins", completed >= 5)
        unlockIf("ten_wins", completed >= 10)
        unlockIf("all_wins", total > 0 && completed >= total)
        unlockIf("stars_15", totalStars >= 15)
        unlockIf("stars_40", totalStars >= 40)
        unlockIf("perfect_5", perfect >= 5)
        unlockIf("no_hint_5", noHint >= 5)
        unlockIf("debugger", usedDebugger)
        unlockIf("polyglot", distinctOpcodes >= 8)
        unlockIf("pixel_artist", graphicsLevel)
        unlockIf("architect", usedRectFill)
        unlockIf("melody", melodyLevel)
        return newly
    }

    fun rankFor(stars: Int): String = when {
        stars >= 100 -> "Silicon Sage"
        stars >= 80 -> "Stack Master"
        stars >= 60 -> "Loop Lord"
        stars >= 40 -> "Loop Apprentice"
        stars >= 25 -> "Register Rider"
        stars >= 12 -> "Bit Twiddler"
        else -> "Bit Rookie"
    }
}
