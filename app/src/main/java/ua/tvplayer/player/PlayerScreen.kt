package ua.tvplayer.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import ua.tvplayer.data.Progress
import ua.tvplayer.data.leadingNumber
import ua.tvplayer.ui.rememberFocus
import ua.tvplayer.ui.rememberFocusMemory
import ua.tvplayer.ui.restoreFocus

private const val SEEK_STEP_MS = 10_000L
private const val HIDE_AFTER_MS = 5_000L
private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)

/**
 * Video with a glass control layer: title block top-left, seek bar, a control row
 * (poster + pills | play | grouped icons) and a strip of episode thumbnails peeking from the bottom.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    player: ExoPlayer,
    session: PlayerSession,
    quality: Int,
    onQuality: (Int) -> Unit,
    onExit: () -> Unit,
) {
    var isPlaying by remember { mutableStateOf(player.playWhenReady) }
    var index by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var heights by remember { mutableStateOf(emptyList<Int>()) }
    var subtitlesOn by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var error by remember { mutableStateOf<PlaybackException?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var firstFrame by remember { mutableStateOf(false) }
    /** Episode whose "next episode" card the viewer cancelled. */
    var upNextCancelled by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing || player.playWhenReady
                // Frames are moving, so one is on screen, whatever order the callbacks arrived in.
                if (playing) firstFrame = true
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
                firstFrame = false
                player.pauseAtEndOfMediaItems = false
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                heights = videoHeights(tracks)
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) error = null
                buffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            // Playing on into the next episode fires no "started playing" event, so watch the clock too.
            if (!firstFrame && player.isPlaying && position > 300) firstFrame = true
            delay(if (duration > 0 && duration - position < UP_NEXT_MS + 1_000) 100 else 500)
        }
    }

    var visible by remember { mutableStateOf(true) }
    val remaining = duration - position
    val upNext = error == null && duration > 0 && index < session.videos.lastIndex &&
        upNextCancelled != index && remaining in 1..UP_NEXT_MS
    // The card takes over the corner; the control bar steps aside while it shows.
    LaunchedEffect(upNext) { if (upNext) visible = false }
    var settingsOpen by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(0L) }
    var returnToSettings by remember { mutableStateOf(false) }

    LaunchedEffect(visible, isPlaying, settingsOpen, showDescription, lastInput) {
        if (visible && isPlaying && !settingsOpen && !showDescription && error == null) {
            delay(HIDE_AFTER_MS)
            visible = false
        }
    }

    fun togglePlay() {
        if (player.playWhenReady) player.pause() else player.play()
    }

    fun seekBy(delta: Long) {
        val known = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val target = (player.currentPosition + delta).coerceAtLeast(0L)
            .let { if (known != null) it.coerceAtMost((known - 1_000L).coerceAtLeast(0L)) else it }
        player.seekTo(target)
        position = target
    }

    fun playEpisode(i: Int) {
        if (i !in session.videos.indices) return
        player.seekTo(i, Progress.resumePosition(session.videos[i].url))
        player.play()
    }

    fun closeSettings() {
        settingsOpen = false
        returnToSettings = true
    }

    // A dropped connection retries by itself every few seconds, from the same position.
    LaunchedEffect(error) {
        if (error != null) {
            visible = true
            delay(5_000)
            player.prepare()
        }
    }

    // Back steps out one layer at a time: menu, description, controls, then the player itself.
    fun back() {
        when {
            upNext -> {
                upNextCancelled = index
                player.pauseAtEndOfMediaItems = true
            }
            settingsOpen -> closeSettings()
            showDescription -> showDescription = false
            visible -> visible = false
            else -> onExit()
        }
    }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        // While the next-episode card shows, it owns the focus.
        if (upNext && !visible) return@LaunchedEffect
        runCatching { if (visible) playFocus.requestFocus() else rootFocus.requestFocus() }
    }
    // When the card goes away (cancelled, or the next episode started), focus returns to the root.
    LaunchedEffect(upNext) { if (!upNext && !visible) runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(settingsOpen, returnToSettings) {
        if (!settingsOpen && returnToSettings) {
            runCatching { settingsFocus.requestFocus() }
            returnToSettings = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            // Focus only rests on the root while controls are hidden; if a closing layer drops it here, go to play.
            .onFocusChanged { if (it.isFocused && visible) runCatching { playFocus.requestFocus() } }
            .focusable()
            .onPreviewKeyEvent { event ->
                // Handled here rather than with BackHandler, which never sees Back on this screen.
                if (event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) back()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInput = System.currentTimeMillis()
                when (event.key) {
                    Key.MediaPlayPause -> { togglePlay(); visible = true; true }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); visible = true; true }
                    Key.MediaFastForward -> { seekBy(SEEK_STEP_MS); visible = true; true }
                    Key.MediaRewind -> { seekBy(-SEEK_STEP_MS); visible = true; true }
                    Key.MediaNext -> { playEpisode(index + 1); true }
                    Key.MediaPrevious -> { playEpisode(index - 1); true }
                    else -> if (visible || upNext) false else {
                        // Hidden controls: center pauses, left/right seek, anything else just reveals the bar.
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter -> togglePlay()
                            Key.DirectionLeft -> seekBy(-SEEK_STEP_MS)
                            Key.DirectionRight -> seekBy(SEEK_STEP_MS)
                        }
                        visible = true
                        true
                    }
                }
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        val loadingStill = if (!firstFrame) {
            session.infos.getOrNull(index)?.image ?: session.videos.getOrNull(index)?.poster ?: session.poster
        } else null
        AnimatedVisibility(
            visible = error == null && (!firstFrame || buffering),
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(450)),
        ) {
            val v = session.videos.getOrNull(index)
            LoadingOverlay(loadingStill, listOfNotNull(v?.season?.ifBlank { null }, v?.title?.ifBlank { null }).joinToString(" · ").ifBlank { null })
        }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Controls(
                session = session,
                index = index,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                subtitlesOn = subtitlesOn,
                showDescription = showDescription,
                playFocus = playFocus,
                settingsFocus = settingsFocus,
                onTogglePlay = ::togglePlay,
                onSeekBy = ::seekBy,
                onEpisode = ::playEpisode,
                onToggleDescription = { showDescription = !showDescription },
                onToggleSubtitles = {
                    subtitlesOn = !subtitlesOn
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesOn)
                        .setPreferredTextLanguage(if (subtitlesOn) "uk" else null)
                        .setSelectUndeterminedTextLanguage(subtitlesOn)
                        .build()
                },
                onOpenSettings = { settingsOpen = true },
            )
        }

        AnimatedVisibility(
            visible = upNext,
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 48.dp),
        ) {
            val next = index + 1
            val nextVideo = session.videos.getOrNull(next)
            val nextInfo = session.infos.getOrNull(next)
            UpNextCard(
                image = nextInfo?.image ?: nextVideo?.poster ?: session.poster,
                title = listOfNotNull(
                    nextVideo?.title?.ifBlank { null },
                    nextInfo?.title,
                ).joinToString(" · "),
                remainingMs = remaining,
                onPlayNow = { playEpisode(next) },
                onCancel = {
                    upNextCancelled = index
                    // Stay on this episode's last frame instead of rolling on.
                    player.pauseAtEndOfMediaItems = true
                },
            )
        }

        error?.let { e ->
            ErrorPanel(
                message = if (e.errorCode in 2000..2999) "Немає з'єднання. Повторюю спробу…" else "Не вдалося відтворити це відео",
                onRetry = {
                    error = null
                    player.prepare()
                    player.play()
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (visible && settingsOpen) {
            SettingsMenu(
                heights = heights,
                quality = quality,
                speed = speed,
                onQuality = {
                    onQuality(it)
                    closeSettings()
                },
                onSpeed = {
                    speed = it
                    player.setPlaybackSpeed(it)
                    closeSettings()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 132.dp),
            )
        }
    }
}

@Composable
private fun Controls(
    session: PlayerSession,
    index: Int,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    subtitlesOn: Boolean,
    showDescription: Boolean,
    playFocus: FocusRequester,
    settingsFocus: FocusRequester,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onEpisode: (Int) -> Unit,
    onToggleDescription: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val video = session.videos.getOrNull(index)
    val info = session.infos.getOrNull(index)
    val episodesFocus = remember { FocusRequester() }
    var episodesFocused by remember { mutableStateOf(false) }
    val hasEpisodes = session.videos.size > 1
    // The thumbnail strip peeks from the bottom edge and slides up once it has focus.
    val shift by animateDpAsState(if (episodesFocused || !hasEpisodes) 0.dp else 104.dp, label = "strip")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )

        // Title block, top-left.
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = 36.dp)
                .fillMaxWidth(0.6f),
        ) {
            Text(
                session.showTitle,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val episodeLine = listOfNotNull(
                video?.season?.ifBlank { null },
                video?.title?.ifBlank { null },
                info?.title,
            ).joinToString("  ·  ")
            if (episodeLine.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(episodeLine, color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val meta = listOfNotNull(
                video?.voice?.ifBlank { null },
                if (duration > 0) "Залишилось ${formatRemaining(duration - position)}" else null,
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(meta, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }
            val description = info?.description ?: session.description
            if (showDescription && description != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    description,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .glass(RoundedCornerShape(22.dp))
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                )
            }
        }

        // Bottom block: seek bar, control row, episode strip.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .offset(y = shift)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 48.dp)) {
                // Fixed slots wide enough for h:mm:ss, so the bar does not shift as the clock runs.
                Text(
                    formatTime(position),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(80.dp),
                )
                SeekBar(
                    fraction = if (duration > 0) position.toFloat() / duration else 0f,
                    onSeekBy = onSeekBy,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatTime(duration),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(80.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 48.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    AsyncImage(
                        model = session.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .glass(RoundedCornerShape(14.dp)),
                    )
                    GlassPill("Опис", onClick = onToggleDescription, active = showDescription)
                    if (hasEpisodes) GlassPill("Серії", onClick = { runCatching { episodesFocus.requestFocus() } })
                }
                GlassCircleButton(
                    if (isPlaying) Glyphs.Pause else Glyphs.Play,
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .focusRequester(playFocus),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    GlassGroup {
                        GlassIconButton(Glyphs.SkipPrevious, onClick = { onEpisode(index - 1) }, enabled = index > 0)
                        GlassIconButton(Glyphs.Rewind, onClick = { onSeekBy(-SEEK_STEP_MS) })
                        GlassIconButton(Glyphs.FastForward, onClick = { onSeekBy(SEEK_STEP_MS) })
                        GlassIconButton(Glyphs.SkipNext, onClick = { onEpisode(index + 1) }, enabled = index < session.videos.lastIndex)
                    }
                    GlassGroup {
                        GlassIconButton(
                            Glyphs.Subtitles,
                            onClick = onToggleSubtitles,
                            enabled = video?.subtitles?.isNotEmpty() == true,
                            active = subtitlesOn,
                        )
                        GlassIconButton(Glyphs.Settings, onClick = onOpenSettings, modifier = Modifier.focusRequester(settingsFocus))
                    }
                }
            }
            if (hasEpisodes) {
                Spacer(Modifier.height(22.dp))
                EpisodeStrip(
                    session = session,
                    current = index,
                    onEpisode = onEpisode,
                    focusRequester = episodesFocus,
                    modifier = Modifier.onFocusChanged { episodesFocused = it.hasFocus },
                )
            }
        }
    }
}

/** Thin track with a glass sphere; left/right seek while it has focus. */
@Composable
private fun SeekBar(fraction: Float, onSeekBy: (Long) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val trackHeight by animateDpAsState(if (focused) 6.dp else 4.dp, label = "track")
    val knobSize by animateDpAsState(if (focused) 26.dp else 18.dp, label = "knob")
    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(32.dp)
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekBy(-SEEK_STEP_MS); true }
                    Key.DirectionRight -> { onSeekBy(SEEK_STEP_MS); true }
                    else -> false
                }
            },
    ) {
        val clamped = fraction.coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White)))
            )
        }
        Box(
            Modifier
                .offset(x = maxWidth * clamped - knobSize / 2)
                .size(knobSize)
                .glassKnob()
        )
    }
}

@Composable
private fun EpisodeStrip(
    session: PlayerSession,
    current: Int,
    onEpisode: (Int) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    val memory = rememberFocusMemory()
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.restoreFocus(memory),
    ) {
        itemsIndexed(session.videos, key = { i, it -> "$i|${it.url}" }) { i, video ->
            val info = session.infos.getOrNull(i)
            EpisodeThumb(
                image = info?.image ?: video.poster ?: session.poster,
                title = info?.let { "${leadingNumber(video.title) ?: i + 1}. ${it.title}" } ?: video.title,
                progress = Progress.fraction(video.url),
                isCurrent = i == current,
                onClick = { onEpisode(i) },
                modifier = Modifier
                    .rememberFocus(memory, preferred = i == current)
                    .then(if (i == current) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
    }
}

@Composable
private fun EpisodeThumb(
    image: String?,
    title: String,
    progress: Float,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .width(196.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                val scale = if (focused) 1.08f else 1f
                scaleX = scale
                scaleY = scale
            }
            .glass(shape, focused)
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(1.dp).clip(shape),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
        )
        if (isCurrent) {
            Text(
                "Зараз",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .glass(RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress > 0.01f) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color.White))
                }
            }
        }
    }
}

@Composable
private fun SettingsMenu(
    heights: List<Int>,
    quality: Int,
    speed: Float,
    onQuality: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    Column(
        modifier
            .width(280.dp)
            .glass(RoundedCornerShape(26.dp))
            .padding(vertical = 14.dp, horizontal = 10.dp),
    ) {
        MenuHeader("Якість")
        (listOf(0) + heights).forEach { height ->
            MenuOption(
                text = if (height == 0) "Авто" else "${height}p",
                selected = height == quality,
                onClick = { onQuality(height) },
                modifier = if (height == quality) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
        Spacer(Modifier.height(8.dp))
        MenuHeader("Швидкість")
        SPEEDS.forEach { value ->
            MenuOption(
                text = if (value == 1f) "Звичайна" else "${value}×",
                selected = value == speed,
                onClick = { onSpeed(value) },
            )
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun MenuOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.Transparent)
                .border(1.dp, Color.White.copy(alpha = if (selected) 1f else 0.3f), CircleShape)
        )
        Spacer(Modifier.width(14.dp))
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun videoHeights(tracks: Tracks): List<Int> =
    tracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group -> (0 until group.length).filter(group::isTrackSupported).map { group.getTrackFormat(it).height } }
        .filter { it > 0 }
        .distinct()
        .sortedDescending()

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatRemaining(ms: Long): String {
    val minutes = ((ms + 59_999) / 60_000).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60} год ${minutes % 60} хв" else "$minutes хв"
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
            .glass(RoundedCornerShape(28.dp))
            .padding(horizontal = 36.dp, vertical = 26.dp),
    ) {
        Text(message, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        GlassPill("Спробувати ще", onClick = onRetry, modifier = Modifier.focusRequester(focus))
    }
}
