package com.growjee.app

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AppVm(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    val db = Db.get(ctx)

    /** Bumped after every change (also by the timer/receivers) so screens re-read the mutable JSON. */
    val tick: Int get() = Bus.tick
    var session by mutableStateOf<JSONObject?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            ctx, "growjee_session",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        session = try { prefs.getString("s", null)?.let { JSONObject(it) } } catch (_: Exception) { null }
        Sync.hook = { viewModelScope.launch { syncCloud() } }
        // Never let a cloud load overwrite changes made offline (e.g. by the focus timer while the app was closed).
        if (session != null) viewModelScope.launch { if (db.dirty) syncCloud() else loadCloud() }
    }

    override fun onCleared() { Sync.hook = null }

    private fun msg(text: String) { messages.tryEmit(text) }
    val email: String get() = session?.optJSONObject("user")?.optString("email").orEmpty()
    private val userId: String get() = session?.optJSONObject("user")?.optString("id").orEmpty()

    fun master(): JSONArray = db.master()
    fun records(key: String): JSONArray = db.records(key)

    private fun now() = System.currentTimeMillis() / 1000

    // ───────── Auth ─────────
    private fun storeSession(s: JSONObject?) {
        session = s
        prefs.edit().apply { if (s == null) remove("s") else putString("s", s.toString()) }.apply()
    }

    private suspend fun accessToken(): String {
        val s = session ?: throw ApiException("Sign in again.")
        if (s.optLong("expires_at", 0) > now() + 60) return s.getString("access_token")
        val fresh = Api.request("/auth/v1/token?grant_type=refresh_token", "POST", JSONObject().put("refresh_token", s.optString("refresh_token"))) as? JSONObject
            ?: throw ApiException("Session expired.")
        fresh.put("expires_at", now() + fresh.optLong("expires_in", 3600))
        storeSession(fresh)
        return fresh.getString("access_token")
    }

    /** Returns an error message, or null on success. */
    suspend fun authenticate(email: String, password: String, create: Boolean): String? {
        if (email.isBlank() || password.length < 6) return "Enter an email and password of at least six characters."
        return try {
            val s = Api.request(
                if (create) "/auth/v1/signup" else "/auth/v1/token?grant_type=password", "POST",
                JSONObject().put("email", email.trim()).put("password", password)
            ) as? JSONObject
            if (s == null || !s.has("access_token")) return "Confirm your email, then sign in."
            s.put("expires_at", now() + s.optLong("expires_in", 3600))
            storeSession(s)
            loadCloud()
            null
        } catch (e: Exception) {
            "Sign-in failed: ${e.message}"
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try { Api.request("/auth/v1/logout", "POST", null, accessToken()) } catch (_: Exception) { }
            storeSession(null)
        }
    }

    // ───────── Sync ─────────
    suspend fun loadCloud() {
        if (session == null) return
        syncing = true
        try {
            val token = accessToken()
            val rows = Api.request("/rest/v1/jee_user_data?select=payload&user_id=eq." + URLEncoder.encode(userId, "UTF-8"), "GET", null, token) as? JSONArray
            val payload = rows?.optJSONObject(0)?.optJSONObject("payload")
            if (payload != null) db.root = JSONObject(payload.toString()) else push(token)
            db.save(); db.dirty = false
            Bus.tick++
        } catch (e: Exception) {
            msg("Cloud sync paused: ${e.message}")
        } finally {
            syncing = false
        }
    }

    private suspend fun push(token: String) {
        val body = JSONObject().put("user_id", userId).put("payload", JSONObject(db.root.toString())).put("updated_at", Instant.now().toString())
        Api.request("/rest/v1/jee_user_data?on_conflict=user_id", "POST", body, token, "resolution=merge-duplicates,return=minimal")
    }

    private suspend fun syncCloud() {
        if (session == null) return
        syncing = true
        try {
            val token = accessToken()
            push(token)
            Api.request(
                "/rest/v1/jee_shares?student_id=eq." + URLEncoder.encode(userId, "UTF-8"), "PATCH",
                JSONObject().put("progress_snapshot", snapshot()).put("snapshot_updated_at", Instant.now().toString()), token, "return=minimal"
            )
            db.dirty = false
        } catch (e: Exception) {
            msg("Saved on this device. Cloud sync paused: ${e.message}")
        } finally {
            syncing = false
        }
    }

    /** Persist locally, refresh the UI, then push to the cloud in the background. */
    fun commit() {
        db.dirty = true
        db.save()
        Bus.tick++
        viewModelScope.launch { syncCloud() }
    }

    suspend fun share(teacherEmail: String): String? {
        if (teacherEmail.isBlank()) return "Enter your teacher's account email."
        return try {
            val body = JSONObject()
                .put("student_id", userId).put("student_email", email)
                .put("teacher_email", teacherEmail.trim().lowercase())
                .put("progress_snapshot", snapshot())
                .put("snapshot_updated_at", Instant.now().toString())
            Api.request("/rest/v1/jee_shares", "POST", body, accessToken(), "return=minimal")
            null
        } catch (e: Exception) {
            "Could not share: ${e.message}"
        }
    }

    /**
     * What a teacher sees: the original summary fields plus the complete data set
     * (every chapter and every record list). Matches the desktop app's snapshot (version 2).
     */
    private fun snapshot(): JSONObject {
        val daily = records("daily").objs(); val questions = records("questions").objs(); val mocks = records("mocks").objs(); val master = master().objs()
        val attempt = questions.sumOf { it.num("Attempted") }; val correct = questions.sumOf { it.num("Correct") }

        val weak = JSONArray()
        master.filter { it.str("mockMarks") != "" && stars(it.num("mockMarks")) <= 2 }.sortedBy { it.num("mockMarks") }.forEach {
            weak.put(JSONObject().put("subject", it.str("subject")).put("chapter", it.str("chapter")).put("score", it.num("mockMarks")).put("stars", stars(it.num("mockMarks"))))
        }
        val recent = JSONArray()
        mocks.sortedByDescending { it.str("Date") }.take(8).forEach {
            recent.put(JSONObject().put("date", it.str("Date")).put("name", it.str("Test name")).put("exam", it.str("Exam")).put("score", it.str("Total score")).put("max", it.str("Total max")))
        }
        val progress = JSONArray()
        for (s in SUBJECTS) {
            val all = master.filter { it.str("subject") == s }
            progress.put(JSONObject().put("subject", s).put("complete", all.count { it.str("status") == "Completed" }).put("total", all.size))
        }

        val fullRecords = JSONObject((db.root.optJSONObject("records") ?: JSONObject()).toString())
        fullRecords.optJSONArray("mocks")?.objs()?.forEach { m ->
            for (k in listOf("questionPdf", "answerPdf")) m.optJSONObject(k)?.apply { remove("localCache"); remove("key") } // never leak device paths
        }

        return JSONObject()
            .put("version", 2).put("scope", "full")
            .put("studyMinutes", daily.sumOf { it.num("Duration (min)") })
            .put("questionsAttempted", attempt).put("questionsCorrect", correct)
            .put("accuracy", if (attempt == 0.0) JSONObject.NULL else Math.round(correct / attempt * 100).toDouble())
            .put("chaptersComplete", master.count { it.str("status") == "Completed" }).put("chaptersTotal", master.size)
            .put("subjectProgress", progress).put("priorityChapters", weak).put("mockTests", recent)
            .put("master", JSONArray(db.master().toString()))
            .put("records", fullRecords)
            .put("generatedAt", Instant.now().toString())
    }

    // ───────── Editing ─────────
    fun mockName(id: String): String =
        records("mocks").objs().firstOrNull { it.str("id") == id }?.str("Test name", "Mock test") ?: if (id.isEmpty()) "No mock linked" else "Saved mock"

    fun linkedCount(id: String) = records("mistakes").objs().count { it.str("Mock test") == id }

    fun save(page: String, current: JSONObject?, edited: JSONObject) {
        val chapter = page == "Chapter Master"
        val target = if (chapter) master() else records(keyOf(page))
        if (current == null) {
            edited.put("id", UUID.randomUUID().toString())
            target.put(edited)
        } else {
            val i = target.indexOfId(current.str("id"))
            if (i >= 0) target.put(i, edited)
        }
        if (chapter && current != null && edited.str("mockMarks") != current.str("mockMarks") && edited.str("mockMarks") != "") {
            records("chapterMarks").put(JSONObject().put("id", UUID.randomUUID().toString()).put("chapterId", edited.str("id"))
                .put("subject", edited.str("subject")).put("chapter", edited.str("chapter")).put("score", edited.num("mockMarks")).put("date", LocalDate.now().toString()))
        }
        if (page == "Mistake Register") edited.put("Mock test label", mockName(edited.str("Mock test")))
        commit()
        msg("Saved")
    }

    fun delete(key: String, item: JSONObject) {
        val arr = if (key == "master") master() else records(key)
        val i = arr.indexOfId(item.str("id"))
        if (i >= 0) arr.remove(i)
        commit()
        msg("Deleted")
    }

    // ───────── Backup ─────────
    fun exportTo(uri: Uri) {
        try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(db.root.toString(2).toByteArray()) }
            msg("Backup exported")
        } catch (e: Exception) { msg("Export failed: ${e.message}") }
    }

    fun importFrom(uri: Uri) {
        try {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            db.root = JSONObject(text)
            db.dirty = true; db.save(); Bus.tick++
            viewModelScope.launch { syncCloud() }
            msg("Backup imported")
        } catch (e: Exception) { msg("Could not import backup: ${e.message}") }
    }

    // ───────── Mock papers ─────────
    fun attachPdf(mock: JSONObject, kind: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val cr = ctx.contentResolver
                val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "$kind.pdf"
                val bytes = withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { it.readBytes() } }
                if (bytes.size > 10 * 1024 * 1024) { msg("PDFs must be 10 MB or smaller."); return@launch }
                if (session == null && kind == "answer") { msg("Sign in to store an answer sheet in cloud storage."); return@launch }
                val local = File(File(ctx.filesDir, "papers").also { it.mkdirs() }, "${mock.str("id")}-$kind.pdf")
                withContext(Dispatchers.IO) { local.writeBytes(bytes) }
                val meta = JSONObject().put("name", name).put("size", bytes.size)
                if (session != null) {
                    try {
                        val path = "$userId/${mock.str("id")}/$kind.pdf"
                        Api.uploadPdf(path, bytes, accessToken())
                        meta.put("storage", "supabase").put("path", path).put("localCache", local.absolutePath)
                    } catch (e: Exception) {
                        if (kind != "question") { msg("Cloud PDF upload failed: ${e.message}"); return@launch }
                        meta.put("storage", "local").put("key", local.absolutePath)
                        msg("Cloud upload failed. The question paper is saved on this device only.")
                    }
                } else meta.put("storage", "local").put("key", local.absolutePath)
                mock.put(kind + "Pdf", meta)
                commit()
                msg(if (kind == "question") "Question paper attached" else "Answer sheet attached")
            } catch (e: Exception) { msg("Could not attach PDF: ${e.message}") }
        }
    }

    fun openPdf(mock: JSONObject, kind: String) {
        viewModelScope.launch {
            try {
                val meta = mock.optJSONObject(kind + "Pdf") ?: run { msg("No PDF attached."); return@launch }
                var file = File(meta.optString("key", meta.optString("localCache")))
                if (meta.optString("storage") == "supabase") {
                    val bytes = Api.downloadPdf(meta.getString("path"), accessToken())
                    file = File(ctx.cacheDir, "pdfs/${UUID.randomUUID()}.pdf").also { it.parentFile!!.mkdirs() }
                    withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                } else if (!file.exists() || !file.absolutePath.startsWith(ctx.filesDir.absolutePath)) {
                    throw ApiException("This device does not have a copy of that local PDF.")
                }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
                ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                msg("No PDF viewer app is installed.")
            } catch (e: Exception) { msg("Could not open PDF: ${e.message}") }
        }
    }
}
