package org.olo.player.art

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 받아 온 가사를 '저장'하고 다시 읽는 지점. 세 자리에 둔다(가장 이식성 높은 순):
 *  ① 곡 옆 사이드카 `<이름>.lrc` -- 폴더에 쓸 수 있을 때만(API 29↓·SD 등). PC·다른 앱도 본다.
 *  ② 파일 태그 임베드 -- [MediaTagSaver]가 담당(API 30+는 사용자 동의). 파일과 함께 이동한다.
 *  ③ 앱 내부 보관 -- 항상 가능(스코프 저장소·원격 FTP/SMB 포함). 적어도 이 앱에선 늘 다시 뜬다.
 *
 * 여기서는 ①·③만 맡는다(②는 태그 쓰기 인프라 재사용). 읽기는 ③을 돌려줘 호출부가 4단 중 한 단으로
 * 끼운다. 모든 파일 IO는 실패해도 조용히 넘긴다(저장 못해도 재생·표시는 계속).
 */
object LyricsStore {

    private fun storeDir(context: Context): File =
        File(context.filesDir, "lyrics").apply { runCatching { mkdirs() } }

    /** 앱 보관 파일(곡의 prefKey로 키를 잡아 경로가 길거나 특수문자여도 안전). */
    private fun appStoreFile(context: Context, prefKey: String): File =
        File(storeDir(context), sha1(prefKey) + ".lrc")

    /** 앱 보관에서 가사 원문(UTF-8). 없으면 null. */
    fun readAppStore(context: Context, prefKey: String): String? =
        appStoreFile(context, prefKey).takeIf { it.isFile }
            ?.let { runCatching { it.readText() }.getOrNull() }
            ?.ifBlank { null }

    /** 앱 보관에 저장(UTF-8). 성공 여부. */
    fun writeAppStore(context: Context, prefKey: String, text: String): Boolean =
        runCatching { appStoreFile(context, prefKey).writeText(text); true }.getOrElse { false }

    /** 곡 옆 사이드카 경로(`<이름>.lrc`). */
    fun sidecarFile(audio: File): File = File(audio.parentFile, audio.nameWithoutExtension + ".lrc")

    /** 폴더에 쓸 수 있으면 사이드카 .lrc로 저장. 쓸 수 없으면(스코프 저장소 등) false. */
    fun writeSidecarIfPossible(audio: File, text: String): Boolean = runCatching {
        val dir = audio.parentFile ?: return false
        if (!dir.canWrite()) return false
        sidecarFile(audio).writeText(text)
        true
    }.getOrElse { false }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
