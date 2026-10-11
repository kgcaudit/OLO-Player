package org.olo.player.art

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 태그 편집의 '읽기'와 '쓰기 계약'. 읽기는 안드로이드 기본기(MediaMetadataRetriever)로 지금
 * 구현하고, 쓰기는 포맷별 태그를 다루는 라이브러리가 필요해 [TagWriter] 인터페이스로만 둔다
 * (실제 구현은 jaudiotagger 연결 시 끼운다). 읽기/쓰기 모두 로컬 파일만 대상이다.
 */

/** 앨범아트 편집 의도. null(미지정)=그대로, Remove=삭제, Set=바이트로 교체. */
sealed interface ArtworkEdit {
    data object Remove : ArtworkEdit
    data class Set(val bytes: ByteArray, val mime: String = "image/jpeg") : ArtworkEdit {
        override fun equals(other: Any?): Boolean =
            other is Set && mime == other.mime && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = 31 * mime.hashCode() + bytes.contentHashCode()
    }
}

/** 쓰기 결과. 포맷 미지원·실패는 원본을 건드리지 않고 사유를 돌려준다. */
sealed interface TagWriteResult {
    data object Ok : TagWriteResult
    data object Unsupported : TagWriteResult
    data class Failed(val reason: String) : TagWriteResult
}

/**
 * 한 로컬 파일의 태그를 안전하게 쓰는 계약. 구현은 임시파일에 쓰고 원자적으로 교체해, 도중
 * 실패가 원본을 깨지 않게 해야 한다(네트워크 파일·미지원 포맷은 [supports]=false).
 */
interface TagWriter {
    fun supports(file: File): Boolean
    fun write(file: File, edits: Map<TagField, FieldEdit>, artwork: ArtworkEdit?): TagWriteResult
}

/** 라이브러리가 붙기 전까지의 기본 구현 -- 아무것도 쓰지 않고 Unsupported를 돌려준다. */
object NoopTagWriter : TagWriter {
    override fun supports(file: File): Boolean = false
    override fun write(file: File, edits: Map<TagField, FieldEdit>, artwork: ArtworkEdit?): TagWriteResult =
        TagWriteResult.Unsupported
}

/** 태그 쓰기를 지원할 포맷(확장자, 점 없이 소문자). jaudiotagger가 쓸 수 있는 집합. */
val TAG_WRITABLE_EXTENSIONS: Set<String> =
    setOf("mp3", "flac", "m4a", "m4b", "ogg", "oga", "opus", "wav")

/**
 * 파일에서 편집 UI 초기값을 읽는다. MediaMetadataRetriever로 표준 태그를, embeddedPicture로
 * 앨범아트를 가져온다. 코멘트처럼 retriever가 주지 않는 필드는 비워 두고(편집·저장은 가능),
 * WAV 등 비표준 컨테이너는 [readWavTags]로 보완한다. 어떤 실패에도 예외 없이 빈 값/ null.
 */
object TagReader {

    /** 현재 태그를 [TrackTags]로. 실패하면 빈 태그. */
    fun read(file: File): TrackTags = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val base = mapFields { key -> r.extractMetadata(key) }
            if (base.values.isNotEmpty()) base else wavFallback(file)
        } finally {
            runCatching { r.release() }
        }
    }.getOrElse { wavFallback(file) }

    /** 파일에 심긴 앨범아트 원본 바이트, 없으면 null. */
    fun readArtwork(file: File): ByteArray? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            r.embeddedPicture
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()

    // 순수 매핑: 키 추출기를 받아 TagField로 접는다(테스트는 가짜 추출기로 검증). 빈 값은 제외.
    fun mapFields(extract: (Int) -> String?): TrackTags {
        fun v(key: Int): String? = extract(key)?.trim()?.ifBlank { null }
        val out = LinkedHashMap<TagField, String>()
        v(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { out[TagField.TITLE] = it }
        v(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { out[TagField.ARTIST] = it }
        v(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { out[TagField.ALBUM] = it }
        v(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.let { out[TagField.ALBUM_ARTIST] = it }
        v(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let { out[TagField.TRACK] = it }
        v(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let { out[TagField.DISC] = it }
        // 연도는 YEAR가 비면 DATE(예: "20240131")의 앞 4자리로 보완.
        (v(MediaMetadataRetriever.METADATA_KEY_YEAR)
            ?: v(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4))?.let { out[TagField.YEAR] = it }
        v(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { out[TagField.GENRE] = it }
        v(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let { out[TagField.COMPOSER] = it }
        return TrackTags(out)
    }

    // 표준 태그가 비는 WAV 등을 위한 보완(기존 readWavTags 재사용). 제목/아티스트/앨범만 채운다.
    private fun wavFallback(file: File): TrackTags {
        val w = runCatching { readWavTags(file) }.getOrNull() ?: return TrackTags()
        val out = LinkedHashMap<TagField, String>()
        w.title?.let { out[TagField.TITLE] = it }
        w.artist?.let { out[TagField.ARTIST] = it }
        w.album?.let { out[TagField.ALBUM] = it }
        return TrackTags(out)
    }
}
