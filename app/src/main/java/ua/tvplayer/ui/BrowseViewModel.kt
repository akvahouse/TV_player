package ua.tvplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import ua.tvplayer.data.CatalogFilter
import ua.tvplayer.data.FilterOptions
import ua.tvplayer.data.History
import ua.tvplayer.data.HistoryEntry
import ua.tvplayer.data.Item
import ua.tvplayer.data.Progress
import ua.tvplayer.data.Section
import ua.tvplayer.data.UaSerial
import ua.tvplayer.data.showKey
import ua.tvplayer.data.userMessage

data class GridState(
    val items: List<Item> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Rail destinations, and the tabs across the top of Home. Section tabs use their path as the key. */
object Destinations {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val WATCHING = "watching"
    const val MAIN = "main"
}

class BrowseViewModel : ViewModel() {
    var destination by mutableStateOf(Destinations.HOME)
    var tab by mutableStateOf(Destinations.MAIN)
    var hero by mutableStateOf<Item?>(null)
        private set
    private var userFocused = false

    val rows = mutableStateMapOf<String, List<Item>>()
    var homeError by mutableStateOf<String?>(null)
        private set

    /** Current filter per section path, and loaded pages per filter. */
    private val filters = mutableStateMapOf<String, CatalogFilter>()
    val grids = mutableStateMapOf<CatalogFilter, GridState>()
    var filterOptions by mutableStateOf<FilterOptions?>(null)
        private set

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<Item>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    /** Card to focus: the one opened before going to its details page, or the first card at launch. */
    var restoreUrl by mutableStateOf<String?>(null)
        private set
    private var startFocusDone = false

    init {
        loadHome()
        viewModelScope.launch { History.importOnce(Progress.allLastWatched(), ::findHistoryEntry) }
    }

    /**
     * Old progress only knows the show key (uaserial.tv/<slug>), which has no page of its own.
     * Try the first season page, then a search for the slug, and read title and poster from there.
     */
    private suspend fun findHistoryEntry(key: String, videoUrl: String): HistoryEntry? {
        val slug = key.substringAfterLast('/')
        val details = runCatching { UaSerial.details("$key/season-1") }.getOrNull()
            ?: UaSerial.search(slug.replace('-', ' ').trimEnd { it.isDigit() })
                .firstOrNull { showKey(it.url) == key }
                ?.let { UaSerial.details(it.url) }
            ?: return null
        val video = details.videos.firstOrNull { it.url == videoUrl }
        return HistoryEntry(
            key = key,
            url = details.url,
            title = details.title,
            poster = details.poster,
            episode = video?.let(History::episodeLabel),
            videoUrl = videoUrl,
            time = 0,
        )
    }

    fun loadHome() {
        homeError = null
        UaSerial.SECTIONS.forEachIndexed { index, section ->
            viewModelScope.launch {
                runCatching { UaSerial.list(section.path) }
                    .onSuccess {
                        rows[section.path] = it.items
                        if (!userFocused && (hero == null || index == 0)) hero = it.items.firstOrNull()
                    }
                    .onFailure { homeError = it.userMessage() }
            }
        }
    }

    /** At launch, put focus on the first card instead of the sidebar. */
    fun focusAtStart(url: String) {
        if (startFocusDone) return
        startFocusDone = true
        restoreUrl = url
    }

    fun open(item: Item) {
        restoreUrl = item.url
    }

    fun restored() {
        restoreUrl = null
    }

    fun focusItem(item: Item) {
        userFocused = true
        hero = item
    }

    fun filterFor(section: Section) = filters[section.path] ?: CatalogFilter(section.path.trimStart('/'))

    fun setFilter(section: Section, filter: CatalogFilter) {
        filters[section.path] = filter
    }

    fun loadFilterOptions() {
        if (filterOptions != null) return
        viewModelScope.launch {
            runCatching { UaSerial.filterOptions() }.onSuccess { options ->
                filterOptions = options.copy(years = options.years.sortedByDescending { it.value })
            }
        }
    }

    fun loadMore(filter: CatalogFilter) {
        val state = grids[filter] ?: GridState()
        if (state.loading || !state.hasNext) return
        grids[filter] = state.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { UaSerial.catalog(filter, state.page + 1) }
                .onSuccess { page ->
                    grids[filter] = GridState(
                        items = (state.items + page.items).distinctBy { it.url },
                        page = state.page + 1,
                        hasNext = page.hasNext && page.items.isNotEmpty(),
                    )
                }
                .onFailure { grids[filter] = state.copy(loading = false, error = it.userMessage()) }
        }
    }

    /** Searches as the user types, after a short pause. */
    fun updateQuery(value: String) {
        query = value
        searchJob?.cancel()
        if (value.trim().length < 2) {
            results = emptyList()
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(600)
            searching = true
            val found = runCatching { UaSerial.search(value.trim()) }
            ensureActive()
            results = found.getOrDefault(emptyList())
            searching = false
        }
    }
}
