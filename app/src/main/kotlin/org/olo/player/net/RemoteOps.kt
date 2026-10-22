package org.olo.player.net

/**
 * 원격 파일/폴더 쓰기 작업의 공통 추상. 브라우저 세션(FTP/SFTP/SMB/WebDAV)이 각자 라이브러리로
 * 구현한다.
 *
 * 체계적 핵심: 프로토콜마다 네이티브로 되는 작업이 다르다. 이름변경·이동·삭제·새 폴더는 네
 * 프로토콜 모두 네이티브라 공통으로 두고, 서버측 복사만 WebDAV(COPY)에만 있어 [caps]로 가린다.
 * 경로는 모두 브라우저가 쓰는 원격 경로(RemoteEntry.path)와 같은 규약을 쓴다. 실패는 예외로 던져
 * 호출부(UI)가 안내 문구로 바꾼다. 세션의 list와 같은 직렬화/재접속 규칙을 따른다.
 */
interface RemoteOps {
    val caps: RemoteOpsCaps

    /** [from]을 [to]로 옮긴다 -- 이름변경(같은 폴더, 이름만)과 이동(다른 폴더)의 공용 기본기. */
    fun rename(from: String, to: String)

    /** [path]를 지운다. 폴더면 안의 내용까지 재귀로 지운다(영구 삭제 -- 원격엔 휴지통 없음). */
    fun delete(path: String, isDir: Boolean)

    /** 전체 경로 [path]에 새 폴더를 만든다. */
    fun mkdir(path: String)

    /** [from]을 [to]로 복사한다(원본 유지). [caps].copy가 true인 소스에서만 호출된다. */
    fun copy(from: String, to: String)
}

/**
 * 한 프로토콜이 네이티브로 지원하는 작업. rename/move/delete/mkdir는 네 프로토콜 모두 지원하므로
 * 플래그를 두지 않고 항상 가능으로 본다. 서버측 복사는 WebDAV만 네이티브라, FTP/SFTP/SMB에서
 * 다운로드+업로드로 흉내 내다 대용량·부분전송 오류를 내는 대신 메뉴에서 '복사'를 가린다.
 */
data class RemoteOpsCaps(val copy: Boolean)

/** 경로의 부모 디렉터리("/a/b/c" -> "/a/b", 루트 보호). 이름변경·이동의 대상 경로 조립에 쓴다. */
fun remoteParentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    val cut = trimmed.lastIndexOf('/')
    return if (cut <= 0) "/" else trimmed.substring(0, cut)
}

/** 경로의 마지막 이름("/a/b/c.mkv" -> "c.mkv"). */
fun remoteNameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')

/** [dir] 아래에 [name]을 둔 경로를 만든다(이중 슬래시 없이). */
fun remoteJoin(dir: String, name: String): String =
    if (dir.endsWith("/")) "$dir$name" else "$dir/$name"
