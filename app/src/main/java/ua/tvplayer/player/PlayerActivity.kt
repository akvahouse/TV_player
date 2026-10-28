package ua.tvplayer.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import ua.tvplayer.data.EpisodeInfo
import ua.tvplayer.data.History
import ua.tvplayer.data.Progress
import ua.tvplayer.data.Subtitle
import ua.tvplayer.data.UaSerial
import ua.tvplayer.data.Video
import ua.tvplayer.ui.AppTheme

/** Everything the player screen shows besides the video itself. */
data class PlayerSession(
    val showTitle: String,
    val showKey: String,
    val poster: String?,
    val description: String?,
    val videos: List<Video>,
    val infos: List<EpisodeInfo?>,
)

/** Plays a list of episodes in one voice, continuing to the next one automatically. */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var quality by mutableIntStateOf(0)
    private lateinit var session: PlayerSession
    private var index = 0
    /** Duration of the current episode, kept so it is still known when the episode is left. */
    private var lastDuration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        session = decode(intent)
        if (session.videos.isEmpty()) {
            finish()
            return
        }
        index = (savedInstanceState?.getInt(EXTRA_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0))
            .coerceIn(0, session.videos.lastIndex)
        quality = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_QUALITY, 0)

        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    // Keyed on the instance, so a new player after stop/start gets fresh screen state.
                    player?.let { key(it) { PlayerScreen(it, session, quality, onQuality = ::changeQuality, onExit = ::finish) } }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (session.videos.isEmpty()) return
        val videos = session.videos
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(UaSerial.USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to UaSerial.PLAYER_REFERER))
            .setAllowCrossProtocolRedirects(true)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory))
            .build()
        exo.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // Leaving an episode by skipping: store where it was left, from the old position.
                if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) return
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return
                val video = videos.getOrNull(oldPosition.mediaItemIndex) ?: return
                if (lastDuration > 0) Progress.save(video.url, oldPosition.positionMs, lastDuration)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                exo.duration.takeIf { it != C.TIME_UNSET }?.let { lastDuration = it }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // The previous episode played to the end.
                    videos.getOrNull(index)?.let { Progress.save(it.url, 1, 1) }
                }
                lastDuration = 0
                index = exo.currentMediaItemIndex
                videos.getOrNull(index)?.let {
                    Progress.setLastWatched(session.showKey, it)
                    History.updateEpisode(session.showKey, it)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    videos.getOrNull(index)?.let { Progress.save(it.url, 1, 1) }
                    finish()
                }
            }

        })
        applyQuality(exo)
        exo.setMediaItems(videos.map(::toMediaItem), index, Progress.resumePosition(videos[index].url))
        exo.playWhenReady = true
        exo.prepare()
        player = exo
    }

    override fun onStop() {
        val exo = player
        player = null
        if (exo != null) {
            savePosition(exo.currentMediaItemIndex, exo)
            exo.release()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, index)
    }

    private fun savePosition(index: Int, exo: ExoPlayer? = player) {
        val video = session.videos.getOrNull(index) ?: return
        if (exo == null || exo.duration == C.TIME_UNSET) return
        Progress.save(video.url, exo.currentPosition, exo.duration)
    }

    private fun changeQuality(height: Int) {
        quality = height
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_QUALITY, height).apply()
        player?.let(::applyQuality)
    }

    /** Pins the stream to one height (kept for the next episodes too), or lets HLS adapt. */
    private fun applyQuality(exo: ExoPlayer) {
        val params = exo.trackSelectionParameters.buildUpon()
        if (quality == 0) {
            params.clearVideoSizeConstraints().setMinVideoSize(0, 0)
        } else {
            params.setMaxVideoSize(Int.MAX_VALUE, quality).setMinVideoSize(0, quality)
        }
        exo.trackSelectionParameters = params.build()
    }

    private fun toMediaItem(video: Video): MediaItem {
        val title = listOf(video.season, video.title).filter { it.isNotBlank() }.joinToString(" · ")
        return MediaItem.Builder()
            .setUri(video.url)
            .setMediaId(video.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title.ifBlank { session.showTitle })
                    .setArtist(session.showTitle)
                    .build()
            )
            .setSubtitleConfigurations(
                video.subtitles.map {
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("uk")
                        .setLabel(it.label)
                        .build()
                }
            )
            .build()
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_VIDEOS = "videos"
        private const val EXTRA_INFOS = "infos"
        private const val EXTRA_POSTER = "poster"
        private const val EXTRA_DESCRIPTION = "description"
        private const val PREFS = "player"
        private const val KEY_QUALITY = "quality"

        fun intent(
            context: Context,
            showTitle: String,
            showKey: String,
            poster: String?,
            description: String?,
            videos: List<Video>,
            infos: List<EpisodeInfo?>,
            index: Int,
        ): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_TITLE, showTitle)
                .putExtra(EXTRA_KEY, showKey)
                .putExtra(EXTRA_POSTER, poster)
                .putExtra(EXTRA_DESCRIPTION, description)
                .putExtra(EXTRA_INDEX, index)
                .putStringArrayListExtra(EXTRA_VIDEOS, ArrayList(videos.map(::encode)))
                .putStringArrayListExtra(EXTRA_INFOS, ArrayList(infos.map(::encodeInfo)))

        // Videos travel as tab-separated lines: season, voice, title, url, poster, then label/url subtitle pairs.
        private fun encode(v: Video): String =
            (listOf(v.season, v.voice, v.title, v.url, v.poster.orEmpty()) + v.subtitles.flatMap { listOf(it.label, it.url) })
                .joinToString("\t") { it.replace('\t', ' ') }

        // Episode info lines: title, description, image; an empty line when there is none.
        private fun encodeInfo(info: EpisodeInfo?): String =
            if (info == null) "" else listOf(info.title, info.description.orEmpty(), info.image.orEmpty())
                .joinToString("\t") { it.replace('\t', ' ') }

        private fun decode(intent: Intent): PlayerSession {
            val videos = intent.getStringArrayListExtra(EXTRA_VIDEOS).orEmpty().map { line ->
                val f = line.split("\t")
                Video(
                    season = f[0],
                    voice = f[1],
                    title = f[2],
                    url = f[3],
                    poster = f[4].ifBlank { null },
                    subtitles = f.drop(5).chunked(2).filter { it.size == 2 }
                        .map { Subtitle(it[0], it[1]) },
                )
            }
            val infos = intent.getStringArrayListExtra(EXTRA_INFOS).orEmpty().map { line ->
                if (line.isEmpty()) return@map null
                val f = line.split("\t")
                EpisodeInfo(f[0], f.getOrNull(1)?.ifBlank { null }, f.getOrNull(2)?.ifBlank { null })
            }
            return PlayerSession(
                showTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                showKey = intent.getStringExtra(EXTRA_KEY).orEmpty(),
                poster = intent.getStringExtra(EXTRA_POSTER),
                description = intent.getStringExtra(EXTRA_DESCRIPTION),
                videos = videos,
                infos = videos.indices.map { infos.getOrNull(it) },
            )
        }
    }
}
