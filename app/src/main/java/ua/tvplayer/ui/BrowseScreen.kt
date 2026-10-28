package ua.tvplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ua.tvplayer.data.CatalogFilter
import ua.tvplayer.data.CatalogSort
import ua.tvplayer.data.FontChoice
import ua.tvplayer.data.FilterOption
import ua.tvplayer.data.History
import ua.tvplayer.data.HistoryEntry
import ua.tvplayer.data.Item
import ua.tvplayer.data.Progress
import ua.tvplayer.data.Section
import ua.tvplayer.data.Settings
import ua.tvplayer.data.TextSize
import ua.tvplayer.data.UaSerial
import ua.tvplayer.data.UniviaWeight
import ua.tvplayer.player.GlassPill
import ua.tvplayer.player.ProvideGlassBackdrop
import ua.tvplayer.player.GlyphIcon
import ua.tvplayer.player.Glyphs
import ua.tvplayer.player.glass

private class NavItem(val key: String, val title: String, val icon: ImageVector)

// MEGOGO keeps the left rail to a few icons and puts the catalogs in tabs across the top.
private val RAIL = listOf(
    NavItem(Destinations.HOME, "Головна", Glyphs.Home),
    NavItem(Destinations.SEARCH, "Пошук", Glyphs.Search),
    NavItem(Destinations.SETTINGS, "Налаштування", Glyphs.Settings),
)

/** Tab key to title. */
private val TABS = listOf(Destinations.MAIN to "Головна") + UaSerial.SECTIONS.map { it.path to it.title }

private val RAIL_WIDTH = 60.dp
private val TAB_GLOW_ROOM = 40.dp
private val CONTENT_START = RAIL_WIDTH + 30.dp

@Composable
fun BrowseScreen(vm: BrowseViewModel, onOpen: (Item) -> Unit) {
    val tabsFocus = remember { FocusRequester() }
    var tabsFocused by remember { mutableStateOf(false) }
    var scrolledDown by remember { mutableStateOf(false) }
    // Set by Back: the tab bar may be hidden (scrolled page), so it is shown first and focused once attached.
    var wantTabs by remember { mutableStateOf(false) }
    LaunchedEffect(wantTabs) {
        if (!wantTabs) return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            if (runCatching { tabsFocus.requestFocus() }.isSuccess && tabsFocused) return@repeat
        }
        wantTabs = false
    }
    // Back walks outwards: page → tabs → Home tab → leave the app.
    BackHandler(enabled = vm.destination != Destinations.HOME || !tabsFocused || vm.tab != Destinations.MAIN) {
        when {
            vm.destination != Destinations.HOME -> vm.destination = Destinations.HOME
            !tabsFocused -> wantTabs = true
            else -> vm.tab = Destinations.MAIN
        }
    }
    val open: (Item) -> Unit = {
        vm.open(it)
        onOpen(it)
    }

    ProvideGlassBackdrop(if (vm.destination == Destinations.HOME) vm.hero?.poster else null) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (vm.destination == Destinations.HOME) Backdrop(vm.hero?.poster)
        val railFocus = remember { FocusRequester() }
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = CONTENT_START)
                // Leaving the page to the left always lands on the rail; lazy grids would otherwise
                // keep the focus on their first column.
                .focusProperties { exit = { if (it == FocusDirection.Left) railFocus else FocusRequester.Default } }
                .focusGroup(),
        ) {
            Crossfade(vm.destination, animationSpec = tween(300), label = "destination") { destination ->
            when (destination) {
                Destinations.SEARCH -> SearchContent(vm, open)
                Destinations.SETTINGS -> SettingsContent()
                else -> Column {
                    // Tabs slide away once the page is scrolled, as in MEGOGO, and return at the top.
                    AnimatedVisibility(!scrolledDown || tabsFocused || wantTabs) {
                        TopTabs(
                            selected = vm.tab,
                            requester = tabsFocus,
                            onFocusChange = { tabsFocused = it },
                            onSelect = { vm.tab = it },
                        )
                    }
                    val onScroll: (Boolean) -> Unit = { scrolledDown = it }
                    // Pages slide in from the side the viewer is moving towards, crossfading as they go.
                    AnimatedContent(
                        targetState = vm.tab,
                        transitionSpec = {
                            val forward = tabOrder(targetState) > tabOrder(initialState)
                            val shift = { width: Int -> (if (forward) width else -width) / 10 }
                            (fadeIn(tween(320, delayMillis = 60)) + slideInHorizontally(tween(380)) { shift(it) }) togetherWith
                                (fadeOut(tween(160)) + slideOutHorizontally(tween(380)) { -shift(it) })
                        },
                        label = "tab",
                    ) { tab ->
                        val section = UaSerial.SECTIONS.firstOrNull { it.path == tab }
                        when {
                            section != null -> CatalogContent(vm, section, open, onScroll)
                            tab == Destinations.WATCHING -> WatchingContent(vm, open, onScroll)
                            else -> HomeContent(vm, open, onScroll)
                        }
                    }
                }
            }
        }
        }
        Rail(selected = vm.destination, selectedFocus = railFocus, onSelect = { vm.destination = it })
    }
    }
}

/** Narrow glass column of icons on the left: Home, Search, Settings. */
@Composable
private fun BoxScope.Rail(selected: String, selectedFocus: FocusRequester, onSelect: (String) -> Unit) {
    var hasFocus by remember { mutableStateOf(false) }
    // Back to Home while the rail has focus: move the focus along with the selection.
    LaunchedEffect(selected) { if (hasFocus) runCatching { selectedFocus.requestFocus() } }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 16.dp)
            .width(RAIL_WIDTH)
            .glass(RoundedCornerShape(30.dp))
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusProperties { enter = { selectedFocus } }
            .focusGroup()
            .padding(7.dp),
    ) {
        RAIL.forEach { item ->
            RailIcon(
                item,
                selected = item.key == selected,
                onClick = { onSelect(item.key) },
                modifier = if (item.key == selected) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun RailIcon(item: NavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                val scale = if (focused) 1.12f else 1f
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(
                when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.16f)
                    else -> Color.Transparent
                },
                CircleShape,
            )
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        GlyphIcon(
            item.icon,
            Modifier.size(22.dp),
            color = if (focused) Color.Black else Color.White.copy(alpha = if (selected) 1f else 0.6f),
        )
    }
}

/**
 * MEGOGO-style tab bar: a bookmark for "Я дивлюся", a divider, then the catalogs. Tabs switch as
 * focus moves across them, and entering the bar always lands on the selected tab.
 */
@Composable
private fun TopTabs(
    selected: String,
    requester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    // Back switches to Home while a tab has focus: move the focus along with the selection.
    LaunchedEffect(selected) { if (hasFocus) runCatching { requester.requestFocus() } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            // With larger text the tabs can outgrow the screen; they scroll instead of wrapping.
            // A scrolling row clips its children, so the focus glow gets room inside it: extra space
            // above and below that the layout then gives back, keeping the page where it was.
            .layout { measurable, constraints ->
                val extra = TAB_GLOW_ROOM.roundToPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height - 2 * extra) { placeable.place(0, -extra) }
            }
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 48.dp, top = 22.dp + TAB_GLOW_ROOM, bottom = 4.dp + TAB_GLOW_ROOM)
            .onFocusChanged {
                hasFocus = it.hasFocus
                onFocusChange(it.hasFocus)
            }
            .focusProperties { enter = { requester } }
            .focusGroup(),
    ) {
        @Composable
        fun tab(key: String, title: String, icon: ImageVector?) = TabItem(
            title = title,
            icon = icon,
            selected = key == selected,
            onFocus = { if (key != selected) onSelect(key) },
            modifier = if (key == selected) Modifier.focusRequester(requester) else Modifier,
        )
        tab(Destinations.WATCHING, "Я дивлюся", Glyphs.Bookmark)
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .width(1.dp)
                .height(26.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )
        TABS.forEach { (key, title) -> tab(key, title, null) }
    }
}

@Composable
private fun TabItem(title: String, icon: ImageVector?, selected: Boolean, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .then(if (focused) Modifier.glass(RoundedCornerShape(50), focused = true) else Modifier)
                .clickable(interaction, indication = null, onClick = onFocus)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            val color = Color.White.copy(alpha = if (selected || focused) 1f else 0.55f)
            if (icon != null) GlyphIcon(icon, Modifier.size(20.dp), color = color)
            // The bookmark tab names itself only when it is the current one, like MEGOGO.
            if (icon == null || selected || focused) {
                if (icon != null) Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    color = color,
                    fontSize = 17.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Box(
            Modifier
                .padding(top = 3.dp)
                .width(28.dp)
                .height(2.dp)
                .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(1.dp))
        )
    }
}

/** Title and a "★ 7.5 • 1 сезон 8 серія" line over the full-screen artwork of the focused card. */
@Composable
private fun HeroInfo(item: Item?, height: Dp) {
    Column(
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(0.6f)
            .height(height),
    ) {
        if (item == null) return@Column
        Text(
            item.title,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 44.sp,
        )
        val meta = listOfNotNull(item.rating?.let { "★ $it" }, item.label).joinToString("  •  ")
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(meta, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(
        text,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(start = 24.dp, top = 30.dp),
    )
}

@Composable
private fun HomeContent(vm: BrowseViewModel, onOpen: (Item) -> Unit, onScroll: (Boolean) -> Unit) {
    val history = History.entries()
    val firstUrl = history.firstOrNull()?.url ?: vm.rows[UaSerial.SECTIONS.first().path]?.firstOrNull()?.url
    LaunchedEffect(firstUrl) { firstUrl?.let(vm::focusAtStart) }
    Column {
        HeroInfo(vm.hero, height = 150.dp)
        val error = vm.homeError
        // Offline: retry every few seconds, so Home fills in once the network is back.
        LaunchedEffect(error) {
            if (error != null) {
                kotlinx.coroutines.delay(5_000)
                vm.loadHome()
            }
        }
        if (vm.rows.isEmpty()) {
            if (error == null) {
                SkeletonRow()
            } else {
                Message(error, start = 24.dp)
                GlassPill("Спробувати ще", onClick = vm::loadHome, modifier = Modifier.padding(start = 24.dp))
            }
            // What was watched is stored locally, so it stays reachable without a network.
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                ContentRow("Продовжити перегляд", history.take(20).map(::historyItem), vm, onOpen = onOpen, tray = true)
            }
            return@Column
        }
        val listState = rememberLazyListState()
        ReportScroll({ listState.firstVisibleItemIndex > 0 }, onScroll)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            modifier = Modifier.fadeTopEdge(),
        ) {
            if (history.isNotEmpty()) {
                item(key = "history") {
                    val progress = history.associate { it.url to Progress.fraction(it.videoUrl) }
                    ContentRow(
                        "Продовжити перегляд",
                        history.take(20).map(::historyItem),
                        vm,
                        onOpen = onOpen,
                        progressOf = { progress[it.url] ?: 0f },
                        tray = true,
                    )
                }
            }
            // Every section keeps its slot while loading, so a late row can't push the list around.
            items(UaSerial.SECTIONS, key = { it.path }) { section ->
                ContentRow(section.title, vm.rows[section.path].orEmpty(), vm, onOpen = onOpen)
            }
        }
    }
}

/** Rows scrolling up fade out under the hero text instead of being cut off. */
private fun Modifier.fadeTopEdge(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 40.dp.toPx() / size.height to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }

/** A row of posters. [tray] sets it in a glass shelf, like the top row of the tvOS home screen. */
@Composable
private fun ContentRow(
    title: String,
    items: List<Item>,
    vm: BrowseViewModel,
    onOpen: (Item) -> Unit,
    progressOf: (Item) -> Float = { 0f },
    tray: Boolean = false,
) {
    Column {
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp),
        )
        if (items.isEmpty()) {
            SkeletonRow(Modifier.padding(top = 14.dp))
            return@Column
        }
        // The tray sits 16dp outside the cards so the first poster lines up with the row title;
        // its corner radius is the card radius plus that inset, so the curves stay concentric.
        val memory = rememberFocusMemory()
        LazyRow(
            contentPadding = if (tray) PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
            else PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .then(
                    if (tray) Modifier.padding(start = 8.dp, end = 24.dp, top = 10.dp).glass(RoundedCornerShape(28.dp))
                    else Modifier
                )
                .restoreFocus(memory),
        ) {
            items(items, key = { it.url }) { item ->
                PosterCard(
                    item,
                    modifier = Modifier.rememberFocus(memory),
                    onClick = { onOpen(item) },
                    onFocus = { vm.focusItem(item) },
                    restoreFocus = item.url == vm.restoreUrl,
                    onRestored = vm::restored,
                    progress = progressOf(item),
                )
            }
        }
    }
}

private enum class FilterMenu { Sort, Genre, Year }

/** A catalog section with MEGOGO-style controls: sort order, genre and year above the grid. */
@Composable
private fun CatalogContent(vm: BrowseViewModel, section: Section, onOpen: (Item) -> Unit, onScroll: (Boolean) -> Unit) {
    val filter = vm.filterFor(section)
    val state = vm.grids[filter] ?: GridState()
    LaunchedEffect(filter) { if (state.items.isEmpty()) vm.loadMore(filter) }
    LaunchedEffect(Unit) { vm.loadFilterOptions() }
    var menu by remember { mutableStateOf<FilterMenu?>(null) }
    // When a menu closes, focus goes back to the button that opened it, not wherever it falls.
    var reopenedFrom by remember { mutableStateOf<FilterMenu?>(null) }
    val pillFocus = remember { FilterMenu.entries.associateWith { FocusRequester() } }
    fun closeMenu() {
        reopenedFrom = menu
        menu = null
    }
    LaunchedEffect(menu, reopenedFrom) {
        val from = reopenedFrom
        if (menu == null && from != null) {
            runCatching { pillFocus.getValue(from).requestFocus() }
            reopenedFrom = null
        }
    }
    BackHandler(enabled = menu != null) { closeMenu() }
    val options = vm.filterOptions

    Box(Modifier.fillMaxSize()) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 24.dp, top = 10.dp),
            ) {
                GlassPill(
                    filter.sort.title,
                    onClick = { menu = FilterMenu.Sort },
                    modifier = Modifier.focusRequester(pillFocus.getValue(FilterMenu.Sort)),
                )
                GlassPill(
                    options?.genres?.firstOrNull { it.value == filter.genre }?.title ?: "Усі жанри",
                    onClick = { menu = FilterMenu.Genre },
                    active = filter.genre != null,
                    modifier = Modifier.focusRequester(pillFocus.getValue(FilterMenu.Genre)),
                )
                GlassPill(
                    filter.year ?: "Усі роки",
                    onClick = { menu = FilterMenu.Year },
                    active = filter.year != null,
                    modifier = Modifier.focusRequester(pillFocus.getValue(FilterMenu.Year)),
                )
                if (filter.genre != null || filter.year != null) {
                    GlassPill("Скинути", onClick = { vm.setFilter(section, filter.copy(genre = null, year = null)) })
                }
            }
            when {
                state.items.isEmpty() && state.error != null -> {
                    Message(state.error, start = 24.dp)
                    GlassPill("Спробувати ще", onClick = { vm.loadMore(filter) }, modifier = Modifier.padding(start = 24.dp))
                }
                state.items.isEmpty() && !state.hasNext -> Message("Нічого не знайдено")
                state.items.isEmpty() -> SkeletonGrid()
                else -> PosterGrid(state.items, vm, onOpen = onOpen, onNearEnd = { vm.loadMore(filter) }, onScroll = onScroll)
            }
        }

        val choose: (CatalogFilter) -> Unit = {
            vm.setFilter(section, it)
            closeMenu()
        }
        when (menu) {
            FilterMenu.Sort -> ChoiceMenu(
                title = "Сортування",
                options = CatalogSort.entries.map { FilterOption(it.name, it.title) },
                selected = filter.sort.name,
                onPick = { value -> choose(filter.copy(sort = CatalogSort.valueOf(value!!))) },
            )
            FilterMenu.Genre -> ChoiceMenu(
                title = "Жанр",
                options = listOf(FilterOption("", "Усі жанри")) + options?.genres.orEmpty(),
                selected = filter.genre.orEmpty(),
                onPick = { choose(filter.copy(genre = it)) },
            )
            FilterMenu.Year -> ChoiceMenu(
                title = "Рік",
                options = listOf(FilterOption("", "Усі роки")) + options?.years.orEmpty(),
                selected = filter.year.orEmpty(),
                onPick = { choose(filter.copy(year = it)) },
            )
            null -> Unit
        }
    }
}

/** Glass drop-down list; an empty value means "no filter" and comes back as null. */
@Composable
private fun ChoiceMenu(title: String, options: List<FilterOption>, selected: String, onPick: (String?) -> Unit) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    Column(
        Modifier
            .padding(start = 24.dp, top = 64.dp)
            .width(300.dp)
            .heightIn(max = 380.dp)
            // Denser than plain glass: the menu sits over busy posters.
            .background(Bg.copy(alpha = 0.88f), RoundedCornerShape(26.dp))
            .glass(RoundedCornerShape(26.dp), seeThrough = false)
            .padding(vertical = 12.dp, horizontal = 10.dp),
    ) {
        Text(
            title.uppercase(),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        LazyColumn {
            items(options, key = { it.value }) { option ->
                MenuRow(
                    option.title,
                    selected = option.value == selected,
                    onClick = { onPick(option.value.ifEmpty { null }) },
                    modifier = if (option.value == selected) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) Color.White.copy(alpha = 0.28f) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(18.dp)) { if (selected) GlyphIcon(Glyphs.Check, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun PosterGrid(
    items: List<Item>,
    vm: BrowseViewModel,
    onOpen: (Item) -> Unit,
    onNearEnd: () -> Unit = {},
    progressOf: (Item) -> Float = { 0f },
    subtitleOf: (Item) -> String? = { null },
    onScroll: (Boolean) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    ReportScroll({ gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 40 }, onScroll)
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(118.dp),
        contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = 18.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.url }) { index, item ->
            if (index == items.lastIndex) LaunchedEffect(items.size) { onNearEnd() }
            PosterCard(
                item,
                onClick = { onOpen(item) },
                onFocus = { vm.focusItem(item) },
                width = null,
                restoreFocus = item.url == vm.restoreUrl,
                onRestored = vm::restored,
                progress = progressOf(item),
                subtitle = subtitleOf(item),
            )
        }
    }
}

/** A history entry shown as a poster card, labelled with the episode the user stopped on. */
private fun historyItem(entry: HistoryEntry) = Item(entry.url, entry.title, entry.poster, entry.episode, rating = null)

private val historyTime = SimpleDateFormat("d MMMM, HH:mm", Locale("uk"))

@Composable
private fun WatchingContent(vm: BrowseViewModel, onOpen: (Item) -> Unit, onScroll: (Boolean) -> Unit) {
    val history = History.entries()
    var confirmClear by remember { mutableStateOf(false) }
    // The backdrop follows the latest watched show here, not whatever was focused on another tab.
    LaunchedEffect(Unit) { history.firstOrNull()?.let { vm.focusItem(historyItem(it)) } }
    Column {
        Text(
            "Історія перегляду",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, top = 18.dp),
        )
        if (history.isEmpty()) {
            Message("Тут з'являться серіали й фільми, які ви дивились")
            return@Column
        }
        GlassPill(
            if (confirmClear) "Натисніть ще раз, щоб очистити" else "Очистити історію",
            onClick = {
                if (confirmClear) History.clear()
                confirmClear = !confirmClear
            },
            active = confirmClear,
            modifier = Modifier.padding(start = 24.dp, top = 14.dp),
        )
        val byUrl = history.associateBy { it.url }
        PosterGrid(
            history.map(::historyItem),
            vm,
            onOpen = onOpen,
            progressOf = { byUrl[it.url]?.let { entry -> Progress.fraction(entry.videoUrl) } ?: 0f },
            subtitleOf = { byUrl[it.url]?.time?.takeIf { it > 0 }?.let { time -> historyTime.format(Date(time)) } },
            onScroll = onScroll,
        )
    }
}

/** A large glass search field, centred until there are results to show below it. */
@Composable
private fun SearchContent(vm: BrowseViewModel, onOpen: (Item) -> Unit) {
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val active = vm.query.trim().length >= 2
    val top by animateDpAsState(if (active) 28.dp else 130.dp, label = "searchTop")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(top))
        if (!active) {
            Text("Пошук", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
        }
        BasicTextField(
            value = vm.query,
            onValueChange = vm::updateQuery,
            singleLine = true,
            interactionSource = interaction,
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 26.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(76.dp)
                // A text field keeps the D-pad to itself; let up/down/left leave it.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                }
                .glass(RoundedCornerShape(38.dp), focused),
            decorationBox = { field ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 26.dp)) {
                    GlyphIcon(Glyphs.Search, Modifier.size(28.dp), alpha = if (focused) 1f else 0.6f)
                    Spacer(Modifier.width(18.dp))
                    Box(Modifier.weight(1f)) {
                        if (vm.query.isEmpty()) {
                            Text("Серіал, мультфільм чи аніме", color = TextDim, fontSize = 26.sp, maxLines = 1)
                        }
                        field()
                    }
                }
            },
        )
        if (!active) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Натисніть, щоб відкрити клавіатуру, або скористайтесь голосовим пошуком пульта",
                color = TextDim,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
        }
        Box(Modifier.fillMaxWidth()) {
            when {
                vm.searching -> Message("Шукаю…", Modifier.align(Alignment.TopCenter))
                active && vm.results.isEmpty() -> Message("Нічого не знайдено", Modifier.align(Alignment.TopCenter))
                else -> PosterGrid(vm.results, vm, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun SettingsContent() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 48.dp),
    ) {
        PageTitle("Налаштування")

        SettingsHeader("Шрифт")
        val fontMemory = rememberFocusMemory()
        val context = LocalContext.current
        val univiaAvailable = remember { univiaAvailable(context) }
        // Univia Pro is only offered when its files are in this build.
        val choices = FontChoice.entries.filter { it != FontChoice.Univia || univiaAvailable }
        LazyRow(
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.restoreFocus(fontMemory),
        ) {
            items(choices, key = { it.name }) {
                FontCard(it, Modifier.rememberFocus(fontMemory, Settings.family == it))
            }
        }

        if (Settings.family != FontChoice.System) {
            SettingsHeader("Насиченість")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 48.dp),
            ) {
                UniviaWeight.entries.forEach { w ->
                    GlassPill(w.title, onClick = { Settings.updateWeight(w) }, active = Settings.weight == w)
                }
            }
        }

        SettingsHeader("Курсив")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 24.dp)) {
            GlassPill("Вимкнено", onClick = { Settings.updateItalic(false) }, active = !Settings.italic)
            GlassPill("Увімкнено", onClick = { Settings.updateItalic(true) }, active = Settings.italic)
        }

        SettingsHeader("Розмір тексту")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 24.dp)) {
            TextSize.entries.forEach { size ->
                GlassPill(size.title, onClick = { Settings.updateTextSize(size) }, active = Settings.textSize == size)
            }
        }

        Text(
            if (univiaAvailable) {
                "Exo 2 і Montserrat поширюються за ліцензією SIL Open Font License. Univia Pro безкоштовний лише для " +
                    "особистого використання; українські літери і, ї, є, ґ у ньому домальовано під цей застосунок."
            } else {
                "Exo 2 і Montserrat поширюються за ліцензією SIL Open Font License."
            },
            color = TextDim.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 24.dp, top = 28.dp, end = 48.dp),
        )
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier.padding(start = 24.dp, top = 26.dp, bottom = 10.dp),
    )
}

/** A preview of one font choice, drawn in that font. */
@Composable
private fun FontCard(choice: FontChoice, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val selected = Settings.family == choice
    val context = LocalContext.current
    val family = remember(choice, Settings.weight, Settings.italic) {
        fontFamilyFor(context, choice, Settings.weight, Settings.italic)
    }
    val style = if (Settings.italic && choice == FontChoice.System) FontStyle.Italic else FontStyle.Normal
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .graphicsLayer {
                val scale = if (focused) 1.08f else 1f
                scaleX = scale
                scaleY = scale
            }
            .width(170.dp)
            .height(112.dp)
            .glass(RoundedCornerShape(22.dp), focused = focused || selected, glow = focused)
            .clickable(interaction, indication = null) { Settings.updateFamily(choice) }
            .padding(14.dp),
    ) {
        Text("Аа Її Єє", color = Color.White, fontSize = 26.sp, fontFamily = family, fontStyle = style, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                choice.title,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontFamily = family,
                fontStyle = style,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (selected) GlyphIcon(Glyphs.Check, Modifier.size(16.dp))
        }
    }
}

/** Tells the tab bar whether the page has scrolled away from its top. */
@Composable
private fun ReportScroll(scrolled: () -> Boolean, onScroll: (Boolean) -> Unit) {
    val value by remember { derivedStateOf(scrolled) }
    LaunchedEffect(value) { onScroll(value) }
}

/** Position of a tab left to right, so transitions know which way to slide. */
private fun tabOrder(key: String): Int = when (key) {
    Destinations.WATCHING -> -1
    else -> TABS.indexOfFirst { it.first == key }
}
