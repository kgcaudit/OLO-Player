package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.olo.player.art.ArtworkEdit
import org.olo.player.art.FieldEdit
import org.olo.player.art.TagField
import org.olo.player.art.TrackTags
import org.olo.player.ui.components.CpField
import org.olo.player.ui.theme.OloTheme

/**
 * 단일 파일 태그 편집 시트. 현재 태그로 필드를 채우고(없으면 빈칸), 바꾼 필드만 저장에 싣는다
 * (Mp3tag Tag Panel 형).
 *
 * 공간을 두 구획으로 체계화한다: '정보 보강'(앨범아트·가사·재생시간) → '기본 정보'(제목·아티스트…).
 * 앨범아트와 가사는 각각 **버튼 하나**('커버 ›' '가사 ›')만 보이고, 누르면 열리는 하위 시트에
 * 찾기·기기에서·삭제를 모은다 -- 편집 화면을 심플하게 유지하기 위함. 세부 작업과 저장·권한은
 * 호출부(호스트)가 처리한다. 앨범아트 삭제 의도([removeArt])도 호스트가 들고 있다가 저장에 싣는다.
 *
 * @param currentArt 파일에 지금 심긴 앨범아트(없으면 null). 미리보기용.
 * @param replacementArt 새로 고른 커버(없으면 null). 있으면 저장 시 교체.
 * @param removeArt 앨범아트 삭제 의도(하위 시트에서 토글, 호스트가 보관).
 * @param durationSec 재생 시간(초). 가사 시간 매칭 기준으로 읽기전용 표기. 0이면 '—'.
 * @param lyricsSaved 이 곡에 저장된 가사(.lrc·앱)가 있는지 -- '가사' 배지와 하위 시트 삭제 활성화.
 */
@Composable
fun TagEditSheet(
    displayName: String,
    tags: TrackTags,
    currentArt: ByteArray? = null,
    replacementArt: ByteArray? = null,
    removeArt: Boolean = false,
    durationSec: Int = 0,
    lyricsSaved: Boolean = false,
    // 커버 선택 후 '메타데이터 보강'에서 고른 값(TagField→값). 편집중값에 덮어써 저장에 실린다.
    enrich: Map<TagField, String>? = null,
    onOpenArtSheet: () -> Unit,
    onOpenLyricsSheet: () -> Unit,
    onSave: (edits: Map<TagField, FieldEdit>, artwork: ArtworkEdit?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 원본값(없으면 빈칸)과 편집중값. 저장 때 다른 필드만 edits로 만든다.
    val original = remember(tags) { TagField.entries.associateWith { (tags[it] ?: "") } }
    val edited = remember(tags) { mutableStateMapOf<TagField, String>().apply { putAll(original) } }
    // 보강에서 고른 값은 편집중값에 반영한다(원본과 달라지므로 저장에 실린다). 커버 피커 왕복으로
    // 이 시트가 재구성돼도 호스트의 enrich로 다시 채운다.
    androidx.compose.runtime.LaunchedEffect(enrich) {
        enrich?.forEach { (f, v) -> edited[f] = v }
    }

    OloCardDialog(
        title = "태그 편집",
        onDismiss = onDismiss,
        actions = {
            OloDialogButton("취소", onClick = onDismiss, primary = false)
            OloDialogButton("저장", onClick = {
                val edits = TagField.entries.mapNotNull { f ->
                    val now = edited[f] ?: ""
                    if (now == original[f]) null else f to FieldEdit.Set(now)
                }.toMap()
                val art: ArtworkEdit? = when {
                    replacementArt != null -> ArtworkEdit.Set(replacementArt)
                    removeArt -> ArtworkEdit.Remove
                    else -> null
                }
                onSave(edits, art)
            })
        },
    ) {
        TagEditContent(
            displayName = displayName,
            edited = edited,
            removeArt = removeArt,
            currentArt = currentArt,
            replacementArt = replacementArt,
            durationSec = durationSec,
            lyricsSaved = lyricsSaved,
            onOpenArtSheet = onOpenArtSheet,
            onOpenLyricsSheet = onOpenLyricsSheet,
        )
    }
}

/**
 * 편집 시트의 알맹이. Dialog 밖에서도 그릴 수 있게 분리해, 대조 렌더(스크린샷)와 일괄 편집 시트가
 * 같은 몸을 쓴다. '정보 보강'(앨범아트·가사·재생시간) → '기본 정보' 두 구획.
 */
@Composable
internal fun TagEditContent(
    displayName: String,
    edited: MutableMap<TagField, String>,
    removeArt: Boolean,
    currentArt: ByteArray?,
    replacementArt: ByteArray?,
    durationSec: Int = 0,
    lyricsSaved: Boolean = false,
    onOpenArtSheet: () -> Unit,
    onOpenLyricsSheet: () -> Unit,
) {
    val c = OloTheme.colors
    Text(displayName, color = c.muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(bottom = 10.dp))

    // ── 정보 보강 ─────────────────────────────────────────────
    SectionLabel("정보 보강")
    Spacer(Modifier.height(8.dp))

    // 앨범아트: 미리보기 + 단일 버튼('커버 ›'). 찾기·기기에서·삭제는 버튼을 눌러 열리는 시트에.
    val artModel: Any? = replacementArt ?: currentArt?.takeIf { !removeArt }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(c.accent), contentAlignment = Alignment.Center) {
            if (artModel != null) {
                AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            } else {
                Icon(Icons.Filled.Album, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("앨범아트", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp)); StateBadge(if (artModel != null) "등록됨" else "없음", artModel != null)
            }
            Text("찾기 · 기기에서 · 삭제", color = c.muted, fontSize = 10.sp)
        }
        SmallBtn("커버 ›", primary = true, onClick = onOpenArtSheet)
    }

    Spacer(Modifier.height(10.dp)); Divider()
    Spacer(Modifier.height(10.dp))

    // 가사: 단일 버튼('가사 ›'). 찾기·기기에서(.lrc)·삭제는 하위 시트에.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("가사", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp)); StateBadge(if (lyricsSaved) "등록됨" else "없음", lyricsSaved)
            }
            Text("찾기 · 기기에서(.lrc) · 삭제", color = c.muted, fontSize = 10.sp)
        }
        SmallBtn("가사 ›", primary = true, onClick = onOpenLyricsSheet)
    }

    Spacer(Modifier.height(10.dp)); Divider()
    Spacer(Modifier.height(10.dp))

    // 재생 시간: 읽기전용. 가사 시간 매칭이 이 값을 기준으로 삼는다.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("재생 시간", color = c.muted, fontSize = 12.sp, modifier = Modifier.width(72.dp))
        Text(if (durationSec > 0) mmss(durationSec) else "—", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }

    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
    Spacer(Modifier.height(12.dp))

    // ── 기본 정보 ─────────────────────────────────────────────
    SectionLabel("기본 정보")
    Spacer(Modifier.height(4.dp))
    TagRow(TagField.TITLE, edited)
    TagRow(TagField.ARTIST, edited)
    TagRow(TagField.ALBUM, edited)
    TagRow(TagField.ALBUM_ARTIST, edited)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { TagRow(TagField.TRACK, edited, KeyboardType.Number) }
        Box(Modifier.weight(1f)) { TagRow(TagField.DISC, edited, KeyboardType.Number) }
    }
    TagRow(TagField.YEAR, edited, KeyboardType.Number)
    TagRow(TagField.GENRE, edited)
    TagRow(TagField.COMPOSER, edited)
    TagRow(TagField.COMMENT, edited)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = OloTheme.colors.accent, fontSize = 11.sp, letterSpacing = 0.3.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(OloTheme.colors.divider))
}

@Composable
private fun StateBadge(text: String, on: Boolean) {
    val c = OloTheme.colors
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) c.accentContainer else c.divider).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = if (on) c.onAccentContainer else c.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TagRow(field: TagField, edited: MutableMap<TagField, String>, keyboard: KeyboardType = KeyboardType.Text) {
    CpField(
        label = field.label,
        value = edited[field] ?: "",
        onValueChange = { edited[field] = it },
        keyboardType = keyboard,
    )
}

private fun mmss(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

@Composable
internal fun SmallBtn(text: String, primary: Boolean, onClick: () -> Unit) {
    val c = OloTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) c.accent else Color.Transparent)
            .border(if (primary) 0.dp else 1.dp, if (primary) Color.Transparent else c.accent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (primary) Color.White else c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
