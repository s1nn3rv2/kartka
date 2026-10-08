package com.s1nn3rv2.kartka

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.os.FileObserver
import com.s1nn3rv2.kartka.index.IndexedNote
import com.s1nn3rv2.kartka.index.SearchResult
import com.s1nn3rv2.kartka.index.search
import com.s1nn3rv2.kartka.markdown.BlockBuilder
import com.s1nn3rv2.kartka.markdown.MdBlock
import com.s1nn3rv2.kartka.storage.LoadedNote
import com.s1nn3rv2.kartka.storage.NoteMeta
import com.s1nn3rv2.kartka.storage.NotesRepository
import com.s1nn3rv2.kartka.storage.PathRules
import com.s1nn3rv2.kartka.storage.TextFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class NotesState(
    val loaded: Boolean = false,
    /** Newest first. */
    val notes: List<NoteMeta> = emptyList(),
    /** Relative folder paths, "" is the notes folder itself. */
    val folders: List<String> = emptyList(),
    /** Bumped whenever note contents in the index change, so previews refresh. */
    val indexVersion: Int = 0,
)

/**
 * App-wide notes state: the chosen folder, the list of notes and the in-memory search index. The folder on disk is
 * the source of truth; this is rebuilt by scanning on start and kept fresh by [refresh] (on resume) and folder
 * watching (changes from Syncthing).
 */
class Notes(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val resolver = context.applicationContext.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val index = ConcurrentHashMap<String, IndexedNote>()
    private val refreshLock = Mutex()
    private var watcher: FileObserver? = null
    private var pendingRefresh: Job? = null

    private val _state = MutableStateFlow(NotesState())
    val state: StateFlow<NotesState> = _state.asStateFlow()

    var rootPath: String?
        get() = prefs.getString("root", null)
        set(value) {
            prefs.edit().putString("root", value).apply()
            index.clear()
            _state.value = NotesState()
            scope.launch { refresh() }
        }

    /** Where notes created from home and quick capture go ("" = the notes folder itself). */
    var defaultFolder: String
        get() = prefs.getString("defaultFolder", "") ?: ""
        set(value) = prefs.edit().putString("defaultFolder", value).apply()

    val repository: NotesRepository? get() = rootPath?.let { NotesRepository(File(it)) }

    /** True when the folder is set and readable (with Storage Scopes, only folders added as a scope are). */
    fun canRead(): Boolean = rootPath?.let { File(it).run { isDirectory && canRead() && listFiles() != null } } == true

    /**
     * Rescans the folder and re-reads only notes whose size or modified time changed. On the first scan it also
     * purges scratch notes and trash older than 30 days.
     */
    suspend fun refresh() = refreshLock.withLock {
        val repo = repository ?: return@withLock
        withContext(Dispatchers.IO) {
            if (!_state.value.loaded) {
                val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
                repo.purge(NotesRepository.SCRATCH, cutoff)
                repo.purge(NotesRepository.TRASH, cutoff)
            }
            val (metas, folders) = repo.scan()
            val sorted = metas.sortedByDescending { it.modified }
            // Show the list right away; contents fill in below.
            _state.value = _state.value.copy(loaded = true, notes = sorted, folders = folders.sorted())
            val keep = metas.mapTo(HashSet()) { it.path }
            index.keys.retainAll(keep)
            val reader = Dispatchers.IO.limitedParallelism(4)
            metas.filter { m -> index[m.path]?.meta.let { it == null || it.modified != m.modified || it.size != m.size } }
                .map { m -> async(reader) { runCatching { index[m.path] = IndexedNote(m, repo.read(m.path).text) } } }
                .awaitAll()
            _state.value = _state.value.copy(indexVersion = _state.value.indexVersion + 1)
            watch(repo, folders)
        }
    }

    fun refreshSoon() {
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            delay(500)
            refresh()
        }
    }

    /** Creates an empty note (named after the current date and time) in [folder], or in the scratch folder. */
    suspend fun create(folder: String, scratch: Boolean = false): String? = withContext(Dispatchers.IO) {
        repository?.create(if (scratch) NotesRepository.SCRATCH else folder, "")?.also { refreshSoon() }
    }

    /** A note read and parsed ahead of opening it, so its screen has content from the very first frame. */
    class Prepared(val path: String, val loaded: LoadedNote, val blocks: List<MdBlock>)

    @Volatile private var prepared: Prepared? = null

    /** Reads and parses [path] off the main thread (a few ms; ~30 ms for a 75 KB note); call before opening it. */
    suspend fun prepare(path: String) {
        val repo = repository ?: return
        prepared = runCatching {
            withContext(Dispatchers.IO) { repo.read(path).let { Prepared(path, it, BlockBuilder.build(it.text)) } }
        }.getOrNull()
    }

    /** The prepared note for [path], handed out once. */
    fun takePrepared(path: String): Prepared? = prepared?.takeIf { it.path == path }?.also { prepared = null }

    /** Creates a note holding [text] (shared from another app), named [title] or the date and time. */
    suspend fun createWith(text: String, folder: String, scratch: Boolean, title: String = ""): String? = io { repo ->
        repo.create(if (scratch) NotesRepository.SCRATCH else folder, title).also { repo.write(it, text, TextFormat()) }
    }

    /** Adds [text] at the end of a note, separated by a blank line. */
    suspend fun append(path: String, text: String) = io { repo ->
        val note = repo.read(path)
        val separator = when {
            note.text.isEmpty() -> ""
            note.text.endsWith("\n\n") -> ""
            note.text.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        repo.write(path, note.text + separator + text, note.format)
    }

    /** Notes in the trash or scratch folder, newest first. */
    suspend fun hidden(folder: String): List<NoteMeta> =
        withContext(Dispatchers.IO) { repository?.listHidden(folder).orEmpty() }

    /** Moves a trashed note back to where it was; returns its path. */
    suspend fun restore(trashPath: String): String? = io { it.restore(trashPath) }

    /** Deletes a file for good (trash and scratch only). */
    suspend fun deleteForever(path: String) = io { it.delete(path) }

    suspend fun emptyTrash() = io { repo -> repo.listHidden(NotesRepository.TRASH).forEach { repo.delete(it.path) } }

    /** Moves a note into [folder] (a scratch note "kept" moves out of the scratch folder); returns the new path. */
    suspend fun move(path: String, folder: String): String? = io { it.move(path, folder) }

    private suspend fun <T> io(block: (NotesRepository) -> T): T? =
        withContext(Dispatchers.IO) { repository?.let(block) }.also { refreshSoon() }

    /** Copies a picked photo into the attachments folder; returns its path as seen from the note at [notePath]. */
    suspend fun attach(uri: Uri, notePath: String): String? = io { repo ->
        val extension = resolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
        resolver.openInputStream(uri)?.use { repo.saveAttachment(it, extension) }
            ?.let { PathRules.relative(notePath.substringBeforeLast('/', ""), it) }
    }

    /** The file an image link [url] in the note at [notePath] points to (relative to the note's folder, like PC editors). */
    fun imageFile(notePath: String, url: String): File? =
        repository?.let { File(it.file(notePath).parentFile, Uri.decode(url)).normalize() }

    /** Where `[[target]]` in the note at [from] leads, if that note exists. */
    fun resolveLink(target: String, from: String): String? =
        com.s1nn3rv2.kartka.index.resolveLink(state.value.notes, target, from.substringBeforeLast('/', ""))

    /** Notes that link to [path] with `[[its name]]`, newest first. */
    fun backlinks(path: String): List<NoteMeta> {
        val name = path.substringAfterLast('/').removeSuffix(".md").lowercase()
        return index.values.filter { it.meta.path != path && name in it.links }.map { it.meta }.sortedByDescending { it.modified }
    }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.Default) { search(index.values, query) }

    /** Notes that are indexed, for things like backlinks later. */
    fun indexed(path: String): IndexedNote? = index[path]

    private fun watch(repo: NotesRepository, folders: List<String>) {
        watcher?.stopWatching()
        val dirs = folders.map { repo.file(it) }
        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.CLOSE_WRITE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO
        // Kept in a field: a FileObserver stops when garbage-collected.
        watcher = object : FileObserver(dirs, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && !path.startsWith(".")) refreshSoon()
            }
        }.apply { startWatching() }
    }
}
