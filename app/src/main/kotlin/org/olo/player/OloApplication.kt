package org.olo.player

import android.app.Application
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import org.olo.player.art.RemoteImage
import org.olo.player.art.RemoteImageFetcher
import org.olo.player.art.RemoteImageKeyer

/**
 * The app's Coil image loader is built here so every AsyncImage in the app can
 * load a [RemoteImage] -- a poster sitting on the same ftp/sftp/smb/webdav server
 * as the video -- through the player's verified data sources, while https posters
 * (TMDB) keep using Coil's own loader. Registering it on the Application makes it
 * the process-wide default with no wiring at each call site.
 */
@UnstableApi
class OloApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(RemoteImageKeyer())
                add(RemoteImageFetcher.Factory(this@OloApplication))
            }
            .crossfade(true)
            .build()
}
