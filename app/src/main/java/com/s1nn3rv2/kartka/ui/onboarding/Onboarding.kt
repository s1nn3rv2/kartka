package com.s1nn3rv2.kartka.ui.onboarding

import com.s1nn3rv2.kartka.LocalAppName
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.s1nn3rv2.kartka.Notes

/**
 * First run: file access, then the notes folder. Picking happens in the system folder picker; the app itself then
 * reads the folder with plain file paths. On GrapheneOS, Storage Scopes stands in for full access, and the folder
 * must be added as a scope or it reads as empty.
 */
@Composable
fun OnboardingScreen(notes: Notes, onDone: () -> Unit) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    var unreadable by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        hasAccess = Environment.isExternalStorageManager()
        // Coming back from settings after adding a Storage Scope.
        if (unreadable != null && notes.canRead()) onDone()
        onPauseOrDispose { }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = uri?.let(::treeUriToPath) ?: return@rememberLauncherForActivityResult
        notes.rootPath = path
        if (notes.canRead()) onDone() else unreadable = path
    }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(LocalAppName.current, style = MaterialTheme.typography.displaySmall)
            val message = when {
                !hasAccess -> "${LocalAppName.current} works directly on a folder of markdown files. It needs file access to read and save them."
                unreadable != null -> "${LocalAppName.current} can't read $unreadable yet. On GrapheneOS with Storage Scopes, open the app's settings, " +
                    "go to Permissions, then Files, and add this folder as a scope."
                else -> "Pick the folder with your notes, for example the one Syncthing syncs."
            }
            Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(max = 480.dp))
            when {
                !hasAccess -> Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri()))
                }) { Text("Grant file access") }
                unreadable != null -> {
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                    }) { Text("Open app settings") }
                    OutlinedButton(onClick = { pickFolder.launch(null) }) { Text("Pick another folder") }
                }
                else -> Button(onClick = { pickFolder.launch(null) }) { Text("Choose notes folder") }
            }
        }
    }
}

/** "content://…/tree/primary:Notes" → "/storage/emulated/0/Notes"; SD cards map to "/storage/<id>/…". */
fun treeUriToPath(uri: Uri): String? {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val volume = id.substringBefore(':')
    val rel = id.substringAfter(':', "")
    val base = if (volume == "primary") Environment.getExternalStorageDirectory().path else "/storage/$volume"
    return if (rel.isEmpty()) base else "$base/$rel"
}
