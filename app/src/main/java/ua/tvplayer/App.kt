package ua.tvplayer

import android.app.Application
import androidx.core.content.res.ResourcesCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import ua.tvplayer.data.History
import ua.tvplayer.data.Progress
import ua.tvplayer.data.FontChoice
import ua.tvplayer.data.Settings
import ua.tvplayer.data.UaSerial
import ua.tvplayer.ui.univiaRes

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Progress.init(this)
        History.init(this)
        Settings.init(this)
        checkFont()
    }

    /** A missing font, or one the system refuses, would break every launch; fall back to the system font. */
    private fun checkFont() {
        if (Settings.family != FontChoice.Univia) return
        val res = univiaRes(this, Settings.weight, Settings.italic)
        val loaded = if (res == 0) null else runCatching { ResourcesCompat.getFont(this, res) }.getOrNull()
        if (loaded == null) Settings.updateFamily(FontChoice.System)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(UaSerial.http)
        .crossfade(true)
        .build()
}
