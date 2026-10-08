package com.s1nn3rv2.kartka

import android.content.Context
import android.os.FileObserver
import com.s1nn3rv2.kartka.index.IndexedNote
import com.s1nn3rv2.kartka.index.SearchResult
import com.s1nn3rv2.kartka.index.search
import com.s1nn3rv2.kartka.storage.NoteMeta
import com.s1nn3rv2.kartka.storage.NotesRepository
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
