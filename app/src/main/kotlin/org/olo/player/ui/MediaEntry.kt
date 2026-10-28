package org.olo.player.ui

import android.net.Uri
import java.io.File

/**
 * One item in a playlist, wherever it lives.
 *
 * The player began life playing only local files, so its playlist was a list of
 * java.io.File. Network streaming (http, ftp) does not have a File behind it, so
 * a source is described here instead: the uri media3 plays, a name to show, a
 * stable key for remembering the place, and -- only for a local file -- the File
 * itself, which the sidecar-subtitle scan, the tag reader and the lyric loader
 * need and which a network source simply does without.
 */
data class MediaEntry(
    /** What the player plays: file://, http(s):// or ftp:// (ftp carries creds). */
    val uri: Uri,
    /** The name shown in the title bar and the queue. */
    val name: String,
    /**
     * The key the saved position and subtitle choice hang on. For a network
     * source this is the uri without any credentials, so a password never
     * reaches the preferences file.
     */
    val prefKey: String = uri.toString(),
    /** The file behind a local source; null for a network one. */
    val localFile: File? = null,
    /**
     * 네트워크 소스일 때, 같은 폴더에서 이름으로 찾아낸 사이드카 자막 파일들. 로컬은 재생
     * 시점에 디스크를 직접 스캔하므로 비워 둔다(네트워크는 디스크가 없어, 폴더를 이미 나열한
     * 브라우저가 여기에 담아 넘긴다).
     */
    val externalSubs: List<ExternalSub> = emptyList(),
) {
    /** 네트워크 사이드카 자막 한 개: 재생 URI(자격증명 포함 가능)와 원래 파일명. */
    data class ExternalSub(val uri: Uri, val fileName: String)

    /** The name without its extension, for the queue and the lyrics screen. */
    val nameWithoutExtension: String get() = name.substringBeforeLast('.', name)
}
