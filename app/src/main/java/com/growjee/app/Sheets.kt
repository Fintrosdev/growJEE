package com.growjee.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

// ───────────────────────── Add / edit ─────────────────────────
@Composable
fun EditSheet(vm: AppVm, page: String, current: JSONObject?, onClose: () -> Unit) {
    val chapter = page == "Chapter Master"
    val fields = remember(page) { fieldsOf(page) }
    val mocks = remember { vm.records("mocks").objs() }
    val values = remember {
        mutableStateMapOf<String, String>().also { m ->
            fields.forEach { f ->
                val key = if (chapter) chapterKey(f.name) else f.name
                var v = current?.str(key) ?: ""
                choicesOf(f.type)?.let { if (v !in it) v = it[0] }          // same default as the desktop app
                if (f.type == "date" && v.isBlank()) v = LocalDate.now().toString()
                m[f.name] = v
            }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

    GlassSheet(onClose) {
        GText((if (current == null) "Add" else "Edit") + " · " + page, 20f, C.Main, FontWeight.SemiBold)
        GText(if (current == null) "Fill in what you know. You can edit it later." else "Update the details and save.", 12f, C.Muted, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
        fields.forEach { f ->
            val v = values[f.name] ?: ""
            val opts = choicesOf(f.type)
            Column(Modifier.padding(bottom = 10.dp)) {
                when {
                    f.type == "mockref" -> ChoiceField(
                        f.name, mocks.firstOrNull { it.str("id") == v }?.str("Test name", "Mock test") ?: "No mock linked",
                        listOf("No mock linked") + mocks.map { it.str("Test name", "Mock test") },
                        { pick -> values[f.name] = mocks.firstOrNull { it.str("Test name", "Mock test") == pick }?.str("id") ?: "" }
                    )
                    opts != null -> ChoiceField(f.name, v, opts, { values[f.name] = it })
                    else -> OutlinedTextField(
                        value = v, onValueChange = { values[f.name] = it }, label = { Text(f.name) }, modifier = Modifier.fillMaxWidth(),
                        singleLine = f.type != "textarea", minLines = if (f.type == "textarea") 3 else 1, colors = fieldColors(), shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = if (f.type == "number") KeyboardType.Decimal else KeyboardType.Text)
                    )
                }
            }
        }
        error?.let { GText(it, 13f, C.Rose, modifier = Modifier.padding(bottom = 8.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton("Cancel", onClose, Modifier.weight(1f))
            PrimaryButton("Save entry", {
                val edited = if (current == null) JSONObject() else JSONObject(current.toString())
                fields.forEach { f -> edited.put(if (chapter) chapterKey(f.name) else f.name, (values[f.name] ?: "").trim()) }
                error = when {
                    chapter && edited.str("chapter").isBlank() -> "Add a chapter name first."
                    chapter && edited.str("mockMarks") != "" && (edited.num("mockMarks") < 0 || edited.num("mockMarks") > 100) -> "Marks must be between 0 and 100."
                    page == "Question Bank Log" && edited.num("Correct") > edited.num("Attempted") -> "Correct cannot exceed attempted."
                    else -> null
                }
                if (error == null) { vm.save(page, current, edited); onClose() }
            }, Modifier.weight(1f))
        }
    }
}

// ───────────────────────── Mock test ─────────────────────────
@Composable
fun MockSheet(vm: AppVm, mock: JSONObject, onEdit: () -> Unit, onClose: () -> Unit) {
    val m = remember(vm.tick) { vm.records("mocks").objs().firstOrNull { it.str("id") == mock.str("id") } ?: mock }
    val linked = remember(vm.tick) { vm.records("mistakes").objs().filter { it.str("Mock test") == m.str("id") } }
    var pendingKind by remember { mutableStateOf("question") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.attachPdf(m, pendingKind, uri) }

    GlassSheet(onClose) {
        GText(m.str("Test name", "Mock test"), 20f, C.Main, FontWeight.SemiBold)
        GText("${m.str("Exam")} · ${m.str("Date")} · ${m.str("Total score", "—")} / ${m.str("Total max", "—")}", 12f, C.Muted, modifier = Modifier.padding(top = 3.dp, bottom = 14.dp))
        PrimaryButton("Edit mock", onEdit, Modifier.fillMaxWidth())
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Attach question paper PDF", { pendingKind = "question"; picker.launch(arrayOf("application/pdf")) }, Modifier.fillMaxWidth())
            GhostButton("Attach answer sheet PDF", { pendingKind = "answer"; picker.launch(arrayOf("application/pdf")) }, Modifier.fillMaxWidth())
            if (m.has("questionPdf")) GhostButton("Open question paper · ${m.optJSONObject("questionPdf")?.str("name").orEmpty()}", { vm.openPdf(m, "question") }, Modifier.fillMaxWidth())
            if (m.has("answerPdf")) GhostButton("Open answer sheet · ${m.optJSONObject("answerPdf")?.str("name").orEmpty()}", { vm.openPdf(m, "answer") }, Modifier.fillMaxWidth())
        }
        GText("Linked mistakes · ${linked.size}", 14f, C.Main, FontWeight.SemiBold, Modifier.padding(top = 18.dp, bottom = 4.dp))
        if (linked.isEmpty()) GText("No mistakes are linked to this mock yet.", 12.5f, C.Dim)
        linked.forEach { ListRow(it.str("Chapter", "Unspecified chapter"), "${it.str("Subject")} · ${it.str("Mistake type")}" + it.str("What went wrong").let { w -> if (w.isBlank()) "" else "\n$w" }, "", C.subject(it.str("Subject"))) }
    }
}

// ───────────────────────── Account ─────────────────────────
@Composable
fun AccountSheet(vm: AppVm, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    GlassSheet(onClose) {
        if (vm.session == null) {
            GText("Welcome to GrowJEE", 20f, C.Main, FontWeight.SemiBold)
            GText("Same account as your GrowJEE website and desktop app.", 12f, C.Muted, modifier = Modifier.padding(top = 3.dp, bottom = 14.dp))
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Email") }, colors = fieldColors(), shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, label = { Text("Password") }, colors = fieldColors(), shape = RoundedCornerShape(14.dp), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            info?.let { GText(it, 13f, C.Rose, modifier = Modifier.padding(top = 10.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton(if (busy) "…" else "Create account", { if (!busy) scope.launch { busy = true; info = vm.authenticate(email, password, true); busy = false; if (info == null) onClose() } }, Modifier.weight(1f))
                PrimaryButton(if (busy) "Signing in…" else "Sign in", { if (!busy) scope.launch { busy = true; info = vm.authenticate(email, password, false); busy = false; if (info == null) onClose() } }, Modifier.weight(1f))
            }
        } else {
            GText("Account & cloud sync", 20f, C.Main, FontWeight.SemiBold)
            GText("Signed in as ${vm.email}", 13.5f, C.Cyan, FontWeight.SemiBold, Modifier.padding(top = 6.dp))
            GText("Your progress syncs to your private account. Same login as the website and desktop app.", 12f, C.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
            GText("SHARE WITH YOUR TEACHER", 10.5f, C.Muted, FontWeight.Bold)
            OutlinedTextField(teacher, { teacher = it }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true, label = { Text("Teacher's account email") }, colors = fieldColors(), shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            GText("Read-only. They see everything: chapters and marks, study and question logs, mock scores, mistakes, revisions and targets. It refreshes every time you sync.", 11.5f, C.Dim, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            info?.let { GText(it, 13f, if (it.startsWith("Shared")) C.Green else C.Rose, modifier = Modifier.padding(bottom = 10.dp)) }
            PrimaryButton(if (busy) "Sharing…" else "Share all progress (read-only)", {
                if (!busy) scope.launch { busy = true; info = vm.share(teacher) ?: "Shared. Your teacher can sign in on the GrowJEE website with that email."; busy = false }
            }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton("Sign out", { vm.signOut(); onClose() }, Modifier.weight(1f), C.Rose)
                GhostButton("Done", onClose, Modifier.weight(1f))
            }
        }
    }
}
