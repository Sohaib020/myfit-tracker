package com.myfit.tracker.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.DataEraser
import com.myfit.tracker.data.backup.Backup
import com.myfit.tracker.data.backup.DriveBackup
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Backup & restore: a .zip file you keep anywhere, or your own Google Drive (hidden app folder). */
@Composable
fun BackupScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val sp = remember { ctx.getSharedPreferences("backup", android.content.Context.MODE_PRIVATE) }
    var last by remember { mutableStateOf(sp.getLong("last", 0L)) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var pendingDrive by remember { mutableStateOf<((String) -> Unit)?>(null) }

    fun mark() { val t = System.currentTimeMillis(); sp.edit().putLong("last", t).apply(); last = t }
    fun mb(b: Long) = "%.1f MB".format(b / 1_048_576.0)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Saving backup…"
        scope.launch {
            runCatching { Backup.writeTo(ctx, uri) }.onSuccess { mark(); toaster.show("Backup saved · ${mb(it)}") }.onFailure { toaster.show(it.message ?: "Backup failed") }
            busy = null
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val info = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { Backup.peek(it) } }
            if (info == null) { toaster.show("That isn't a MyFit backup"); return@launch }
            confirm = "Restore the backup from ${DateFormat.getDateTimeInstance().format(Date(info.createdAt))}? Everything on this phone is replaced by it, then MyFit restarts." to {
                withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { Backup.restore(ctx, it) } }
            }
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val k = pendingDrive; pendingDrive = null
        if (res.resultCode != Activity.RESULT_OK || k == null) { busy = null; toaster.show("Google Drive wasn't connected"); return@rememberLauncherForActivityResult }
        runCatching { DriveBackup.tokenFrom(ctx, res.data) }.onSuccess(k).onFailure { busy = null; toaster.show(it.message ?: "Drive access wasn't granted") }
    }

    /** Gets a Drive token (asking the user once), then runs [then]. */
    fun withDrive(label: String, then: suspend (String) -> Unit) {
        val act = ctx as? Activity ?: return
        busy = label
        scope.launch {
            runCatching { DriveBackup.authorize(act) }.onSuccess { a ->
                when (a) {
                    is DriveBackup.Auth.Token -> runCatching { then(a.token) }.onFailure { toaster.show(it.message ?: "Drive failed") }.also { busy = null }
                    is DriveBackup.Auth.NeedsUi -> {
                        pendingDrive = { t -> scope.launch { runCatching { then(t) }.onFailure { toaster.show(it.message ?: "Drive failed") }; busy = null } }
                        consent.launch(IntentSenderRequest.Builder(a.intent.intentSender).build())
                    }
                }
            }.onFailure { busy = null; toaster.show(it.message ?: "Couldn't reach Google") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Backup & restore", { nav.pop() }, if (last > 0) "Last backup: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last)) else "No backup yet")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            busy?.let { Caption(it, color = th.accentBright) }
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Google Drive", style = FitType.section, color = th.text)
                    Caption("Saved in a hidden MyFit folder in your own Drive — only this app can read it. Keeps your 3 newest backups.")
                    Spacer(Modifier.height(12.dp))
                    AccentButton("Back up now", {
                        if (busy == null) withDrive("Backing up to Drive…") { t -> val b = DriveBackup.upload(ctx, t); mark(); toaster.show("Backed up to Drive · ${mb(b)}") }
                    }, Modifier.fillMaxWidth(), icon = Duo.Cloud, height = 48.dp)
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Restore from Drive", {
                        if (busy == null) withDrive("Checking Drive…") { t ->
                            val list = DriveBackup.list(t)
                            val newest = list.firstOrNull()
                            if (newest == null) toaster.show("No MyFit backup in this Drive yet")
                            else confirm = "Restore your Drive backup from ${newest.modified.take(16).replace('T', ' ')} UTC? Everything on this phone is replaced by it, then MyFit restarts." to {
                                DriveBackup.restore(ctx, t, newest.id)
                            }
                        }
                    }, Modifier.fillMaxWidth(), icon = Duo.Sync, height = 48.dp)
                }
            }
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Backup file", style = FitType.section, color = th.text)
                    Caption("One .zip you can keep in Files, send to your PC or another phone.")
                    Spacer(Modifier.height(12.dp))
                    AccentButton("Save backup file", { if (busy == null) save.launch("myfit-backup-${java.time.LocalDate.now()}.zip") }, Modifier.fillMaxWidth(), icon = Duo.Save, height = 48.dp)
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Restore from file", { if (busy == null) open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth(), icon = Duo.Unarchive, height = 48.dp)
                }
            }
            Caption("Included: every log, workout, meal, setting, plan and progress photo. Not included: the offline AI model and buddy packs (they download again). Saved AI keys only work on this phone — re-enter them after restoring elsewhere.",
                Modifier.padding(horizontal = 6.dp))
        }
    }

    confirm?.let { (msg, run) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Replace your data?") },
            text = { Text(msg) },
            confirmButton = { TextButton({
                confirm = null; busy = "Restoring…"
                scope.launch {
                    runCatching { run() }.onSuccess { toaster.show("Restored — restarting"); kotlinx.coroutines.delay(600); DataEraser.restart(ctx) }
                        .onFailure { busy = null; toaster.show(it.message ?: "Restore failed — nothing was changed") }
                }
            }) { Text("Restore", color = th.danger) } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
    }
}
