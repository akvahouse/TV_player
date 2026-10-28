package ua.tvplayer.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ua.tvplayer.data.Details
import ua.tvplayer.data.EpisodeInfo
import ua.tvplayer.data.History
import ua.tvplayer.data.Item
import ua.tvplayer.data.Progress
import ua.tvplayer.data.SimpsonsUa
import ua.tvplayer.data.UaSerial
import ua.tvplayer.data.Video
import ua.tvplayer.data.leadingNumber
import ua.tvplayer.data.userMessage
import ua.tvplayer.data.showKey
import ua.tvplayer.player.PlayerActivity

class DetailsViewModel(val item: Item) : ViewModel() {
    var details by mutableStateOf<Details?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var season by mutableStateOf("")
        private set
    var voice by mutableStateOf("")

    /** Season number → episode number → title/description, for shows simpsonsua.tv covers. */
    private val episodeInfo = mutableStateMapOf<Int, Map<Int, EpisodeInfo>>()

    private val key = showKey(item.url)

    init {
        load()
    }

    fun load() {
        error = null
        viewModelScope.launch {
            runCatching { UaSerial.details(item.url) }
                .onSuccess {
                    details = it
                    pickDefaults(it)
                }
                .onFailure { error = it.userMessage() }
        }
    }

    /** Continue where the user stopped; otherwise open the season this page is about. */
    private fun pickDefaults(d: Details) {
        val last = Progress.lastWatched(key)?.let { url -> d.videos.firstOrNull { it.url == url } }
        val seasons = d.videos.map { it.season }.distinct()
        season = last?.season
            ?: d.pageSeason?.let { n -> seasons.firstOrNull { it.filter(Char::isDigit) == n.toString() } }
            ?: seasons.firstOrNull().orEmpty()
        val voices = voicesFor(season)
        voice = last?.voice
            ?: Progress.preferredVoice(key)?.takeIf { it in voices }
            ?: voices.firstOrNull().orEmpty()
    }

    val seasons: List<String> get() = details?.videos?.map { it.season }?.distinct().orEmpty()

    fun voicesFor(season: String): List<String> =
        details?.videos?.filter { it.season == season }?.map { it.voice }?.distinct().orEmpty()

    val episodes: List<Video>
        get() = details?.videos?.filter { it.season == season && it.voice == voice }.orEmpty()

    fun loadEpisodeInfo() {
        val number = leadingNumber(season) ?: return
        if (!SimpsonsUa.supports(item.url) || number in episodeInfo) return
        episodeInfo[number] = emptyMap()
        viewModelScope.launch {
            runCatching { SimpsonsUa.season(item.url, number) }
                .onSuccess { episodeInfo[number] = it }
                .onFailure { episodeInfo.remove(number) }
        }
    }

    fun infoFor(video: Video): EpisodeInfo? {
        val season = leadingNumber(video.season) ?: return null
        val episode = leadingNumber(video.title) ?: return null
        return episodeInfo[season]?.get(episode)
    }

    fun selectSeason(value: String) {
        season = value
        val voices = voicesFor(value)
        if (voice !in voices) voice = Progress.preferredVoice(key)?.takeIf { it in voices } ?: voices.firstOrNull().orEmpty()
    }

    /** The episode the main button plays: the last one watched, or the next if it was finished. */
    fun resumeIndex(): Int {
        val list = episodes
        val last = Progress.lastWatched(key)
        val index = list.indexOfFirst { it.url == last }
        return when {
            index < 0 -> 0
            Progress.isWatched(list[index].url) && index < list.lastIndex -> index + 1
            else -> index
        }
    }

    fun play(context: Context, index: Int) {
        val d = details ?: return
        val list = episodes
        val video = list.getOrNull(index) ?: return
        Progress.setLastWatched(key, video)
        History.record(item.url, d.title, item.poster ?: d.poster, video)
        context.startActivity(
            PlayerActivity.intent(context, d.title, key, d.poster, d.description, list, list.map(::infoFor), index)
        )
    }
}

@Composable
fun DetailsScreen(item: Item) {
    val vm: DetailsViewModel = viewModel(key = item.url) { DetailsViewModel(item) }
    val context = LocalContext.current
    val d = vm.details
    val playRequester = remember { FocusRequester() }

    // Offline: keep retrying quietly, so the page fills in once the network is back.
    LaunchedEffect(vm.error) {
        if (vm.error != null) {
            kotlinx.coroutines.delay(5_000)
            vm.load()
        }
    }
    LaunchedEffect(d != null) {
        if (d != null && d.videos.isNotEmpty()) runCatching { playRequester.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        Backdrop(d?.poster ?: item.poster)
        // Anchored to the bottom-left when it fits, leaving the artwork clear; the paddings keep text
        // inside the TV's overscan-safe area, and taller pages scroll as before.
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(top = 56.dp, bottom = 52.dp),
        ) {
            Column(Modifier.padding(horizontal = 56.dp).fillMaxWidth(0.46f)) {
                Text(
                    d?.title ?: item.title,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    item.rating?.let { "★ $it" },
                    item.label,
                    vm.voice.ifBlank { null },
                ).joinToString("   ·   ")
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(meta, fontSize = 14.sp, color = TextDim)
                }
                d?.description?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, fontSize = 14.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(18.dp))

            when {
                vm.error != null -> {
                    Message(vm.error.orEmpty())
                    Pill("Спробувати ще", selected = false, onClick = vm::load, modifier = Modifier.padding(start = 56.dp))
                }
                d == null -> DetailsSkeleton()
                d.videos.isEmpty() -> Message(modifier = Modifier.padding(start = 8.dp), text = "Відео для цієї сторінки на сайті поки немає (можливо, лише трейлер)")
                else -> Playback(vm, playRequester, onPlay = { vm.play(context, it) }, fallbackPoster = d.poster)
            }
        }
    }
}

@Composable
private fun Playback(vm: DetailsViewModel, playRequester: FocusRequester, onPlay: (Int) -> Unit, fallbackPoster: String?) {
    val episodes = vm.episodes
    if (episodes.isEmpty()) return
    val resume = vm.resumeIndex()
    val current = episodes[resume]
    val started = Progress.fraction(current.url) > 0.01f && !Progress.isWatched(current.url)
    val label = buildString {
        append(if (started) "▶  Продовжити" else "▶  Дивитись")
        if (episodes.size > 1) append("  ·  ").append(listOf(current.season, current.title).filter { it.isNotBlank() }.joinToString(", "))
    }
    Pill(
        label,
        selected = false,
        primary = true,
        onClick = { onPlay(resume) },
        modifier = Modifier.padding(start = 56.dp).focusRequester(playRequester),
    )

    val seasons = vm.seasons
    if (seasons.size > 1) {
        RowTitle("Сезони")
        PillRow(seasons, vm.season, vm::selectSeason)
    }
    val voices = vm.voicesFor(vm.season)
    if (voices.size > 1) {
        RowTitle("Озвучка")
        PillRow(voices, vm.voice) { vm.voice = it }
    }
    if (episodes.size > 1) {
        LaunchedEffect(vm.season) { vm.loadEpisodeInfo() }
        var focusedIndex by remember(vm.season, vm.voice) { mutableIntStateOf(resume) }
        RowTitle("Серії")
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = resume)
        val memory = rememberFocusMemory()
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.restoreFocus(memory),
        ) {
            itemsIndexed(episodes, key = { i, it -> "$i|${it.url}" }) { index, episode ->
                EpisodeCard(
                    Modifier.rememberFocus(memory, preferred = index == resume),
                    episode,
                    vm.infoFor(episode),
                    fallbackPoster,
                    Progress.fraction(episode.url),
                    onFocus = { focusedIndex = index },
                ) { onPlay(index) }
            }
        }
        val focused = episodes.getOrNull(focusedIndex)
        val info = focused?.let(vm::infoFor)
        if (focused != null && info != null) EpisodeDescription(focused, info)
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier.padding(start = 56.dp, top = 22.dp),
    )
}

@Composable
private fun PillRow(values: List<String>, selected: String, onSelect: (String) -> Unit) {
    val memory = rememberFocusMemory()
    // Entering the row lands on the selected value, not on whichever pill is geometrically closest.
    LaunchedEffect(selected) { memory.reset() }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.restoreFocus(memory),
    ) {
        items(values, key = { it }) { value ->
            Pill(
                value.ifBlank { "Основна" },
                selected = value == selected,
                onClick = { onSelect(value) },
                modifier = Modifier.rememberFocus(memory, preferred = value == selected),
            )
        }
    }
}

private val EpisodeShape = RoundedCornerShape(10.dp)

@Composable
private fun EpisodeCard(
    modifier: Modifier,
    episode: Video,
    info: EpisodeInfo?,
    fallbackPoster: String?,
    progress: Float,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(200.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged {
                    focused = it.isFocused || it.hasFocus
                    if (focused) onFocus()
                },
            shape = CardDefaults.shape(EpisodeShape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = EpisodeShape)),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = info?.image ?: episode.poster ?: fallbackPoster,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (progress > 0.01f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Color.White, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            info?.let { "${leadingNumber(episode.title) ?: ""}. ${it.title}" } ?: episode.title.ifBlank { "Відео" },
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Color.White else TextDim,
        )
    }
}

/** Apple TV-style blurb for the focused episode, under the row. Fixed height keeps the row from jumping. */
@Composable
private fun EpisodeDescription(episode: Video, info: EpisodeInfo) {
    Column(
        Modifier
            .padding(horizontal = 56.dp)
            .fillMaxWidth(0.7f)
            .height(96.dp),
    ) {
        Text(
            listOf(episode.season, episode.title, info.title).filter { it.isNotBlank() }.joinToString("  ·  "),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        info.description?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 14.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
