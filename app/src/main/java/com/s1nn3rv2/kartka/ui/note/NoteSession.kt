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
import kotlinx.coroutines.NonCancellable
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

    private var format by mutableStateOf(TextFormat())

    /** The file isn't valid UTF-8: shown, but never saved (saving would replace its letters). */
    val readOnly get() = format.readOnly

    /** The last save failed (for example storage full); the text stays here and the next save tries again. */
    var saveFailed by mutableStateOf(false)
        private set
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

    /**
     * The file changed on disk (another device, through Syncthing) while this note had edits that weren't saved yet.
     * Saving stops until [keepMine] or [useTheirs]; leaving the note first keeps the edits in a separate copy.
     */
    var conflict by mutableStateOf(false)
        private set

    private fun diskChanged(repo: NotesRepository) = repo.file(path).lastModified().let { it != 0L && it != diskModified }

    val hasUnsavedChanges get() = pendingFromEditor != null || text != savedText

    /** False when the file can't be read (deleted on another device, or renamed before the app restarted). */
    suspend fun load(): Boolean {
        val repo = notes.repository ?: return false
        apply(runCatching { withContext(Dispatchers.IO) { repo.read(path) } }.getOrNull() ?: return false)
        return true
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

    /** Takes over any pending editor text and saves now (Done, back, leaving the note: [final]). */
    fun saveNow(final: Boolean = false) {
        saveJob?.cancel()
        saveJob = scope.launch { fileLock.withLock { saveLocked(final) } }
    }

    /** Overwrites the other device's version with the text here. */
    fun keepMine() {
        val repo = notes.repository ?: return
        scope.launch {
            fileLock.withLock {
                diskModified = withContext(Dispatchers.IO) { repo.file(path).lastModified() }
                conflict = false
                saveLocked()
            }
        }
    }

    /** Drops the edits here and shows the other device's version. */
    fun useTheirs() {
        val repo = notes.repository ?: return
        scope.launch {
            fileLock.withLock {
                pendingFromEditor = null
                conflict = false
                apply(withContext(Dispatchers.IO) { repo.read(path) })
            }
        }
    }

    /** Saves and renames take turns, so a late autosave can never recreate the file under its old name. */
    private val fileLock = Mutex()

    private suspend fun save() = fileLock.withLock { saveLocked() }

    /** Set once the note was moved to the trash: nothing may write it again (that would recreate the file). */
    private var closed = false

    private suspend fun saveLocked(final: Boolean = false) {
        if (closed) return
        pendingFromEditor?.let { current ->
            pendingFromEditor = null
            current().also { editorText = it; text = it }
        }
        val t = text ?: return
        if (t == savedText || format.readOnly) return
        val repo = notes.repository ?: return
        // Once a write starts it runs to the end, bookkeeping included: a keystroke cancelling the autosave
        // halfway would otherwise leave the file newer than this session knows, which looks like a conflict.
        withContext(NonCancellable) {
            if (conflict || withContext(Dispatchers.IO) { diskChanged(repo) }) {
                conflict = true
                if (final && t != copiedText) keepCopy(repo, t)
                return@withContext
            }
            val meta = runCatching { withContext(Dispatchers.IO) { repo.write(path, t, format) } }.getOrNull()
            saveFailed = meta == null
            if (meta == null) return@withContext
            savedText = t
            diskModified = meta.modified
            notes.refreshSoon()
        }
    }

    /** The text last copied to a "(my edits)" note, so leaving or pausing again doesn't copy it twice. */
    private var copiedText: String? = null

    /** A conflict still open when leaving or pausing: the edits go to "<title> (my edits)" next to the note. */
    private suspend fun keepCopy(repo: NotesRepository, t: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                val copy = repo.create(path.substringBeforeLast('/', ""), PathRules.title(path.substringAfterLast('/')) + " (my edits)")
                repo.write(copy, t, format)
            }
            copiedText = t
        }
        notes.refreshSoon()
    }

    /** Renames the file to [title] (illegal characters removed, numbered if taken). Blank titles keep the name. */
    fun rename(title: String) {
        val repo = notes.repository ?: return
        if (title.isBlank() || title == PathRules.title(path.substringAfterLast('/'))) return
        scope.launch {
            fileLock.withLock {
                saveLocked()
                val old = path
                path = runCatching { withContext(Dispatchers.IO) { repo.rename(path, title) } }.getOrDefault(path)
                notes.pathChanged(old, path)
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
                val old = path
                path = runCatching { withContext(Dispatchers.IO) { repo.move(path, folder) } }.getOrDefault(path)
                notes.pathChanged(old, path)
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

    /** Reloads when the file changed on disk (e.g. Syncthing); with unsaved edits here, flags a [conflict] instead. */
    suspend fun reloadIfChangedOnDisk() {
        val repo = notes.repository ?: return
        fileLock.withLock {
            if (closed || !withContext(Dispatchers.IO) { diskChanged(repo) }) return
            if (hasUnsavedChanges) {
                conflict = true
                return
            }
            val loaded = runCatching { withContext(Dispatchers.IO) { repo.read(path) } }.getOrNull() ?: return
            // Typing may have started while the file was being read; then it's a conflict, not a reload.
            if (hasUnsavedChanges) conflict = true else apply(loaded)
        }
    }
}
