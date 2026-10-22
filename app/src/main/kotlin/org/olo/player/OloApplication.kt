package org.olo.player

import android.app.Application
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import org.olo.player.art.RemoteImage
import org.olo.player.art.RemoteImageFetcher
import org.olo.player.art.RemoteImageKeyer
import org.olo.player.data.AppPreferences
import org.olo.player.data.NetConfig

/**
 * The app's Coil image loader is built here so every AsyncImage in the app can
 * load a [RemoteImage] -- a poster sitting on the same ftp/sftp/smb/webdav server
 * as the video -- through the player's verified data sources, while https posters
 * (TMDB) keep using Coil's own loader. Registering it on the Application makes it
 * the process-wide default with no wiring at each call site.
 */
@UnstableApi
class OloApplication : Application(), ImageLoaderFactory {
    // jaudiotagger 루트 로거의 강한 참조. LogManager는 로거를 약참조로만 들고 있어, 참조가 없으면
    // GC 후 기본 레벨로 되살아난다 -- 프로세스 수명 내내 OFF가 유지되도록 여기서 붙잡아 둔다.
    private var jaudiotaggerLogger: java.util.logging.Logger? = null

    override fun onCreate() {
        super.onCreate()
        // 저장된 연결 제한시간을 세션·데이터소스가 읽을 수 있게 한 번 적재한다.
        NetConfig.load(AppPreferences(this))
        // jaudiotagger 로깅 끄기: 안드로이드에서 태그를 읽을 때마다 java.util.logging으로 수많은
        // 로그를 쏟아내고(기본 핸들러가 logcat에 동기 기록) 이게 매우 느려, 가사·커버·태그 읽기
        // 한 번에 수백 ms씩 걸려 재생 버퍼링까지 일으킨다. 프로세스에서 한 번만 꺼 주면 모든 읽기가
        // 빨라진다(널리 알려진 jaudiotagger Android 성능 이슈). 'org.jaudiotagger' 아래 자식 로거는
        // 자체 레벨이 없어 이 레벨을 상속한다.
        runCatching {
            val root = java.util.logging.Logger.getLogger("org.jaudiotagger")
            root.level = java.util.logging.Level.OFF
            root.useParentHandlers = false
            jaudiotaggerLogger = root
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(RemoteImageKeyer())
                add(RemoteImageFetcher.Factory(this@OloApplication))
            }
            .crossfade(true)
            .build()
}
