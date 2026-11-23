package com.growjee.app

import java.time.LocalDate

data class Teaser(val code: String, val unlocksOn: LocalDate, val text: String)

/**
 * Project M teasers. EDIT THIS FILE: change the dates and the copy to whatever you want to hint at.
 * Cards unlock on their date; locked ones show scrambled text. A notification fires on each unlock date
 * (if "Project M signals" is on). The last entry is the big reveal and also drives the dashboard countdown.
 */
object Teasers {
    val DROP_DATE: LocalDate = LocalDate.of(2027, 3, 1)

    val list = listOf(
        Teaser("SIGNAL 01", LocalDate.of(2026, 11, 20), "Something is waking up inside GrowJEE."),
        Teaser("SIGNAL 02", LocalDate.of(2026, 12, 1), "You keep logging. It keeps watching. Quietly."),
        Teaser("SIGNAL 03", LocalDate.of(2026, 12, 15), "Every mistake you record leaves a trace. M remembers."),
        Teaser("SIGNAL 04", LocalDate.of(2027, 1, 5), "Signal strength rising. Keep studying."),
        Teaser("SIGNAL 05", LocalDate.of(2027, 2, 1), "Almost. Don't look away."),
        Teaser("PROJECT M", DROP_DATE, "PROJECT M // ONLINE")
    )

    fun unlocked(today: LocalDate = LocalDate.now()) = list.filter { !it.unlocksOn.isAfter(today) }
}
