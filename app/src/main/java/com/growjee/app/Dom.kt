package com.growjee.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

val SUBJECTS = listOf("Physics", "Chemistry", "Mathematics")

val PAGES = listOf(
    "Dashboard", "Chapter Master", "Daily Study Log", "Question Bank Log",
    "Mock Test Analytics", "Mistake Register", "Revision Scheduler", "Target Planner",
    "Focus Timer", "Distraction Blocker", "Reminders"
)

val ICONS = mapOf(
    "Dashboard" to "✦", "Chapter Master" to "◈", "Daily Study Log" to "◷", "Question Bank Log" to "▤",
    "Mock Test Analytics" to "◉", "Mistake Register" to "⌁", "Revision Scheduler" to "↻", "Target Planner" to "◎",
    "Focus Timer" to "◐", "Distraction Blocker" to "⊘", "Reminders" to "◇"
)

/** Same record keys as the desktop app and website, so cloud data is interchangeable. */
val DATA_KEYS = mapOf(
    "Daily Study Log" to "daily", "Question Bank Log" to "questions", "Mock Test Analytics" to "mocks",
    "Mistake Register" to "mistakes", "Revision Scheduler" to "revisions", "Target Planner" to "targets"
)

fun keyOf(page: String) = DATA_KEYS[page] ?: "daily"

fun JSONObject.str(k: String, d: String = ""): String = if (has(k) && !isNull(k)) opt(k).toString() else d
fun JSONObject.num(k: String): Double = str(k, "0").toDoubleOrNull() ?: 0.0
fun JSONArray.objs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun stars(n: Double) = when { n < 20 -> 0; n < 40 -> 1; n < 60 -> 2; n < 80 -> 3; n < 100 -> 4; else -> 5 }
fun starText(n: Double) = "★".repeat(stars(n)) + "☆".repeat(5 - stars(n))
fun parseDate(s: String): LocalDate? = try { LocalDate.parse(s.take(10)) } catch (e: Exception) { null }

data class Field(val name: String, val type: String)

fun fieldsOf(page: String): List<Field> = when (page) {
    "Chapter Master" -> listOf(Field("Subject", "subject"), Field("Chapter", "text"), Field("Syllabus list", "text"), Field("Status", "status"), Field("Confidence", "confidence"), Field("Recent mock mark / 100", "number"), Field("Notes", "textarea"))
    "Daily Study Log" -> listOf(Field("Date", "date"), Field("Subject", "subject"), Field("Chapter", "text"), Field("Study type", "studytype"), Field("Duration (min)", "number"), Field("Notes", "textarea"))
    "Question Bank Log" -> listOf(Field("Date", "date"), Field("Subject", "subject"), Field("Chapter", "text"), Field("Difficulty", "difficulty"), Field("Source / set", "text"), Field("Attempted", "number"), Field("Correct", "number"), Field("Time (min)", "number"), Field("Notes", "textarea"))
    "Mock Test Analytics" -> listOf(Field("Date", "date"), Field("Exam", "exam"), Field("Test name", "text"), Field("Physics score", "number"), Field("Physics max", "number"), Field("Chemistry score", "number"), Field("Chemistry max", "number"), Field("Mathematics score", "number"), Field("Mathematics max", "number"), Field("Total score", "number"), Field("Total max", "number"), Field("Duration (min)", "number"), Field("Notes", "textarea"))
    "Mistake Register" -> listOf(Field("Date", "date"), Field("Mock test", "mockref"), Field("Subject", "subject"), Field("Chapter", "text"), Field("Mistake type", "mistaketype"), Field("Question / source", "text"), Field("What went wrong", "textarea"), Field("Correct approach", "textarea"), Field("Resolved?", "yesno"))
    "Revision Scheduler" -> listOf(Field("Due date", "date"), Field("Subject", "subject"), Field("Chapter", "text"), Field("Revision number", "number"), Field("Status", "revisionstatus"), Field("Completed date", "date"), Field("Notes", "textarea"))
    else -> listOf(Field("Week starting", "date"), Field("Subject", "subject"), Field("Target type", "targettype"), Field("Target value", "number"), Field("Actual value", "number"), Field("Notes", "textarea"))
}

fun choicesOf(type: String): List<String>? = when (type) {
    "subject" -> SUBJECTS
    "exam" -> listOf("JEE Main", "JEE Advanced")
    "status" -> listOf("Not Started", "In Progress", "Completed")
    "confidence" -> listOf("Low", "Medium", "High")
    "difficulty" -> listOf("Easy", "Moderate", "Hard")
    "studytype" -> listOf("Concept Study", "Problem Practice", "Revision", "Mock Analysis")
    "mistaketype" -> listOf("Conceptual", "Calculation", "Misread", "Time Management", "Guessing", "Test Strategy")
    "yesno" -> listOf("No", "Yes")
    "revisionstatus" -> listOf("Planned", "In Progress", "Completed")
    "targettype" -> listOf("Study Hours", "Questions Attempted")
    else -> null
}

fun chapterKey(name: String) = when (name) {
    "Subject" -> "subject"; "Chapter" -> "chapter"; "Syllabus list" -> "source"; "Status" -> "status"
    "Confidence" -> "confidence"; "Recent mock mark / 100" -> "mockMarks"; else -> "notes"
}

fun describe(page: String) = when (page) {
    "Daily Study Log" -> "Track focused sessions."
    "Question Bank Log" -> "Track attempts and accuracy."
    "Mock Test Analytics" -> "Record scores, attach papers and relate mistakes."
    "Mistake Register" -> "Link errors to the mock where they happened."
    "Revision Scheduler" -> "Plan spaced reviews."
    else -> "Set weekly targets and record actuals."
}
