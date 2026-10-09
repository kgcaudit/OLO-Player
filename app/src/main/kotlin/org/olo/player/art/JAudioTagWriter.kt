package org.olo.player.art

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.reference.PictureTypes

/**
 * jaudiotagger(Adonai Android 포크)로 로컬 음악 파일의 태그·앨범아트를 쓰는 [TagWriter] 구현.
 *
 * 안전 규칙: 원본에 바로 쓰지 않는다. 같은 폴더에 확장자를 유지한 임시 사본을 만들어 거기에
 * 쓰고, 성공하면 원자적 이동으로 원본과 바꾼다. 도중 어떤 실패(읽기·쓰기·포맷)에도 원본은
 * 그대로 남고, 임시 사본은 지운다. 네트워크 파일·미지원 포맷은 [supports]=false로 거른다.
 *
 * (주의: 실제 파일 쓰기·포맷별 동작은 기기에서만 검증된다. 이 환경에선 컴파일까지만 확인.)
 */
object JAudioTagWriter : TagWriter {

    override fun supports(file: File): Boolean =
        file.extension.lowercase() in TAG_WRITABLE_EXTENSIONS

    override fun write(file: File, edits: Map<TagField, FieldEdit>, artwork: ArtworkEdit?): TagWriteResult {
        if (!supports(file)) return TagWriteResult.Unsupported
        if (!file.isFile || !file.canRead()) return TagWriteResult.Failed("파일을 읽을 수 없음")
        if (edits.isEmpty() && artwork == null) return TagWriteResult.Ok // 바꿀 게 없다

        // 확장자를 유지한 임시 사본(같은 폴더라 원자적 이동이 가능). jaudiotagger는 확장자로
        // 포맷을 고르므로 반드시 원본 확장자로 끝나야 한다.
        val tmp = File(file.parentFile, "${file.nameWithoutExtension}.__olotag__.${file.extension}")
        return runCatching {
            file.copyTo(tmp, overwrite = true)

            val audio = AudioFileIO.read(tmp)
            val tag = audio.tagOrCreateAndSetDefault
            for ((field, edit) in edits) {
                val key = field.toFieldKey()
                when (edit) {
                    is FieldEdit.Keep -> Unit
                    is FieldEdit.Set ->
                        // 한 필드가 그 포맷에서 안 되더라도 전체를 깨지 않게 개별로 감싼다.
                        runCatching {
                            if (edit.value.isEmpty()) tag.deleteField(key) else tag.setField(key, edit.value)
                        }
                }
            }
            when (artwork) {
                null -> Unit
                is ArtworkEdit.Remove -> runCatching { tag.deleteArtworkField() }
                is ArtworkEdit.Set -> {
                    val art = AndroidArtwork().apply {
                        binaryData = artwork.bytes
                        mimeType = artwork.mime
                        pictureType = PictureTypes.DEFAULT_ID // 앞표지(front cover)
                    }
                    runCatching { tag.deleteArtworkField() }
                    tag.setField(art)
                }
            }
            AudioFileIO.write(audio)

            // 성공 → 원자적 교체. 같은 폴더라 ATOMIC_MOVE가 보통 통하고, 안 되면 일반 교체로.
            runCatching {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            TagWriteResult.Ok as TagWriteResult
        }.getOrElse { e ->
            runCatching { if (tmp.exists()) tmp.delete() } // 원본 보존, 임시 정리
            TagWriteResult.Failed(e.message ?: e.toString())
        }
    }

    private fun TagField.toFieldKey(): FieldKey = when (this) {
        TagField.TITLE -> FieldKey.TITLE
        TagField.ARTIST -> FieldKey.ARTIST
        TagField.ALBUM -> FieldKey.ALBUM
        TagField.ALBUM_ARTIST -> FieldKey.ALBUM_ARTIST
        TagField.TRACK -> FieldKey.TRACK
        TagField.DISC -> FieldKey.DISC_NO
        TagField.YEAR -> FieldKey.YEAR
        TagField.GENRE -> FieldKey.GENRE
        TagField.COMPOSER -> FieldKey.COMPOSER
        TagField.COMMENT -> FieldKey.COMMENT
    }
}
