package com.s1nn3rv2.kartka.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.s1nn3rv2.kartka.Notes
import com.s1nn3rv2.kartka.storage.LoadedNote
import com.s1nn3rv2.kartka.storage.NotesRepository
import com.s1nn3rv2.kartka.storage.PathRules
import com.s1nn3rv2.kartka.storage.TextFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One open note: its text, how its bytes were stored, and saving. Saves run on [scope] (app-wide, main thread), so a
 * pending save still completes after the screen closes.
 */
class NoteSession(private val notes: Notes, path: String, private val scope: CoroutineScope, initial: LoadedNote? = null) {
    /** Changes when the note is renamed. */
    var path by mutableStateOf(path)
        private set

    var text by mutableStateOf<String?>(null)
        private set

    /** The exact String instance the editor last showed; when it differs from [text], the editor must reload. */
    var editorText: String? = null

    private var format = TextFormat()
    private var savedText: String? = null
    private var diskModified = 0L
    private var saveJob: Job? = null
    private var pendingFromEditor: (() -> String)? = null

    init {
        initial?.let(::apply)
    }

    private fun apply(loaded: LoadedNote) {
        format = loaded.format
        savedText = loaded.text
        diskModified = loaded.modified
        text = loaded.text
    }

    val hasUnsavedChanges get() = pendingFromEditor != null || text != savedText

    suspend fun load() {
        val repo = notes.repository ?: return
        apply(withContext(Dispatchers.IO) { repo.read(path) })
    }

    /** A change made outside the editor (a checkbox tap): shown and saved right away. */
    fun replace(newText: String) {
        text = newText
        saveNow()
    }

    /**
     * The editor changed. Its text is only copied out when saving (600 ms after the last keystroke), not on every
     * keystroke, and marked as already shown in the editor so it does not reload.
     */
    fun editorChanged(current: () -> String) {
        pendingFromEditor = current
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            save()
        }
    }

    /** Takes over any pending editor text and saves now (Done, back, leaving the note). */
    fun saveNow() {
        saveJob?.cancel()
        saveJob = scope.launch { save() }
    }

    /** Saves and renames take turns, so a late autosave can never recreate the file under its old name. */
    private val fileLock = Mutex()

    private suspend fun save() = fileLock.withLock { saveLocked() }

    /** Set once the note was moved to the trash: nothing may write it again (that would recreate the file). */
    private var closed = false

    private suspend fun saveLocked() {
        if (closed) return
        pendingFromEditor?.let { current ->
            pendingFromEditor = null
            current().also { editorText = it; text = it }
        }
        val t = text ?: return
        if (t == savedText) return
        val repo = notes.repository ?: return
        val meta = withContext(Dispatchers.IO) { repo.write(path, t, format) }
        savedText = t
        diskModified = meta.modified
        notes.refreshSoon()
    }

    /** Renames the file to [title] (illegal characters removed, numbered if taken). Blank titles keep the name. */
    fun rename(title: String) {
        val repo = notes.repository ?: return
        if (title.isBlank() || title == PathRules.title(path.substringAfterLast('/'))) return
        scope.launch {
            fileLock.withLock {
                saveLocked()
                path = withContext(Dispatchers.IO) { repo.rename(path, title) }
            }
            notes.refreshSoon()
        }
    }

    /** Saves, then moves the note into [folder]. */
    fun move(folder: String) {
        val repo = notes.repository ?: return
        scope.launch {
            fileLock.withLock {
                saveLocked()
                path = withContext(Dispatchers.IO) { repo.move(path, folder) }
            }
            notes.refreshSoon()
        }
    }

    /** Saves, then moves the note to the trash; returns its path there (for undo), or null if that failed. */
    suspend fun trash(): String? {
        val repo = notes.repository ?: return null
        return fileLock.withLock {
            saveLocked()
            closed = true
            runCatching { withContext(Dispatchers.IO) { repo.trash(path) } }.getOrNull()
        }.also { notes.refreshSoon() }
    }

    val isScratch get() = path.startsWith(NotesRepository.SCRATCH + "/")

    /** A new note left without any text is deleted, so quick captures abandoned halfway leave no empty files behind. */
    fun discardIfEmpty() {
        val repo = notes.repository ?: return
        scope.launch {
            fileLock.withLock {
                saveLocked()
                if (closed || !text.isNullOrBlank()) return@withLock
                closed = true
                withContext(Dispatchers.IO) { repo.file(path).takeIf { it.length() == 0L }?.delete() }
            }
            notes.refreshSoon()
        }
    }

    /** Reloads when the file changed on disk (e.g. Syncthing) and nothing here is unsaved. True if it reloaded. */
    suspend fun reloadIfChangedOnDisk(): Boolean {
        val repo = notes.repository ?: return false
        if (hasUnsavedChanges) return false
        val modified = withContext(Dispatchers.IO) { repo.file(path).lastModified() }
        if (modified == diskModified || modified == 0L) return false
        load()
        return true
    }
}
