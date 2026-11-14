package org.olo.player.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceException
import java.io.IOException

/**
 * 원격 DataSource(FTP·SFTP·SMB·WebDAV)가 던지는 재생 오류 래퍼.
 *
 * 네 구현이 똑같은 식(`DataSourceException(IOException(...), ERROR_CODE_IO_UNSPECIFIED)`)을 각자
 * `err`/`ftpError`로 두던 것을 한곳에 모은다. media3는 DataSourceException으로 와야 재생 오류를
 * 네트워크 I/O 실패로 제대로 분류하므로, 원인 예외를 IOException으로 감싸 코드와 함께 전달한다.
 */
@UnstableApi
fun dataSourceError(message: String, cause: Throwable?): IOException =
    DataSourceException(IOException(message, cause), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
