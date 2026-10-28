package org.olo.player.art

/**
 * 파일명이 드러내는 "이 파일"의 기술 사양 -- 해상도·영상코덱·HDR·음성·출처·컨테이너.
 * 씬(scene) 이름은 제목 뒤에 이런 표식을 관습적으로 박아 두므로(예: "...1080p.BluRay.
 * x264.DTS-HD.MA.5.1-GROUP.mkv"), 네트워크 파일을 열어 보지 않고도 파일명만으로 꽤 정확히
 * 읽어 낼 수 있다(Phase 1). 프레임 단위 정밀값(정확한 비트레이트·자막 트랙 수 등)은 재생
 * 엔진이 열어야 알 수 있어 이후 단계로 미룬다.
 *
 * 못 읽은 항목은 null -- 화면이 그 칩을 그냥 그리지 않는다. 틀린 값을 지어내느니 비운다.
 */
data class MediaTech(
    val resolution: String?,
    val videoCodec: String?,
    val hdr: String?,
    val audio: String?,
    val source: String?,
    val container: String?,
) {
    /** 화면에 뿌릴 칩들(읽힌 것만, 보기 좋은 고정 순서). */
    fun chips(): List<String> = listOfNotNull(resolution, videoCodec, hdr, audio, source, container)

    companion object {
        val EMPTY = MediaTech(null, null, null, null, null, null)

        fun parse(fileName: String): MediaTech {
            // 구분자(점·밑줄·하이픈)를 공백으로 바꿔 토큰 경계를 분명히 한 뒤 소문자로 본다.
            val stem = fileName.substringBeforeLast('.', fileName)
            // 코덱·출처는 점을 공백으로 바꿔 토큰 경계를 분명히 한 t에서, 채널 표기(5.1 등)는
            // 점을 살린 원문에서 읽는다(점을 지우면 "5.1"이 "5 1"로 깨지므로).
            val t = " " + stem.replace(Regex("""[._\-\[\]()]"""), " ").lowercase() + " "
            return MediaTech(
                resolution = resolutionOf(t),
                videoCodec = videoCodecOf(t),
                hdr = hdrOf(t),
                audio = audioOf(t, stem.lowercase()),
                source = sourceOf(t),
                container = containerOf(fileName),
            )
        }

        private fun resolutionOf(t: String): String? = when {
            Regex("""\b(2160p|4k|uhd)\b""").containsMatchIn(t) -> "4K"
            Regex("""\b1080[pi]\b""").containsMatchIn(t) -> "1080p"
            Regex("""\b720p\b""").containsMatchIn(t) -> "720p"
            Regex("""\b(480p|576p)\b""").containsMatchIn(t) -> "SD"
            else -> null
        }

        private fun videoCodecOf(t: String): String? = when {
            Regex("""\b(x265|h\s?265|hevc)\b""").containsMatchIn(t) -> "H.265"
            Regex("""\b(x264|h\s?264|avc)\b""").containsMatchIn(t) -> "H.264"
            Regex("""\bav1\b""").containsMatchIn(t) -> "AV1"
            Regex("""\bxvid\b""").containsMatchIn(t) -> "XviD"
            Regex("""\bdivx\b""").containsMatchIn(t) -> "DivX"
            else -> null
        }

        private fun hdrOf(t: String): String? = when {
            Regex("""\b(dolby\s?vision|dovi|\bdv\b)\b""").containsMatchIn(t) -> "Dolby Vision"
            Regex("""\b(hdr10\+?|hdr)\b""").containsMatchIn(t) -> "HDR"
            else -> null
        }

        // 음성 코덱 하나(가장 구체적인 것 우선)에 채널 표기(5.1 등)가 있으면 붙인다.
        private fun audioOf(t: String, stemLower: String): String? {
            val codec = when {
                Regex("""\batmos\b""").containsMatchIn(t) -> "Atmos"
                Regex("""\btruehd\b""").containsMatchIn(t) -> "TrueHD"
                Regex("""\b(dts\s?hd|dtshd)\b""").containsMatchIn(t) -> "DTS-HD"
                Regex("""\bdts\b""").containsMatchIn(t) -> "DTS"
                Regex("""\b(eac3|ddp\d?|dd\+)\b""").containsMatchIn(t) -> "E-AC3"
                Regex("""\b(ac3|dd\d)\b""").containsMatchIn(t) -> "AC3"
                Regex("""\baac\b""").containsMatchIn(t) -> "AAC"
                Regex("""\bflac\b""").containsMatchIn(t) -> "FLAC"
                Regex("""\bmp3\b""").containsMatchIn(t) -> "MP3"
                else -> null
            } ?: return null
            // 채널은 점을 살린 원문에서 읽는다("5.1"/"7.1"/"2.0"). 앞뒤가 숫자면(연도 등) 무시.
            val channels = Regex("""(?<!\d)([2-9])\.([01])(?!\d)""").find(stemLower)
                ?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }
            return if (channels != null) "$codec $channels" else codec
        }

        private fun sourceOf(t: String): String? = when {
            Regex("""\b(bluray|blu\s?ray|bdrip|brrip|bdremux)\b""").containsMatchIn(t) -> "BluRay"
            Regex("""\b(web\s?dl|webdl)\b""").containsMatchIn(t) -> "WEB-DL"
            Regex("""\bwebrip\b""").containsMatchIn(t) -> "WEBRip"
            Regex("""\bweb\b""").containsMatchIn(t) -> "WEB"
            Regex("""\bhdtv\b""").containsMatchIn(t) -> "HDTV"
            Regex("""\b(dvdrip|dvd)\b""").containsMatchIn(t) -> "DVD"
            else -> null
        }

        private fun containerOf(fileName: String): String? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "ts", "m2ts", "mpg", "mpeg", "m4v", "vob" ->
                    if (ext == "m2ts") "M2TS" else ext.uppercase()
                else -> null
            }
        }
    }
}
