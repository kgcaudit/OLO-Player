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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * (Mp3tag Tag Panel 형). 앨범아트는 지금 심긴 것/골라온 것을 미리보기로 보여 주고 교체·삭제한다.
 *
 * 표현부만 담당한다 -- 저장은 [onSave]로 '바뀐 필드 edits + 앨범아트 의도'만 넘기고, 실제 쓰기·
 * 권한은 호출부(쓰기 엔진)가 처리한다. '커버 찾기'는 [onFindCover]로 커버 피커를 연다.
 *
 * @param currentArt 파일에 지금 심긴 앨범아트(없으면 null). 미리보기용.
 * @param replacementArt 커버 피커에서 새로 고른 커버(없으면 null). 있으면 저장 시 교체로 나간다.
 */
@Composable
fun TagEditSheet(
    displayName: String,
    tags: TrackTags,
    currentArt: ByteArray? = null,
    replacementArt: ByteArray? = null,
    // 커버 선택 후 '메타데이터 보강'에서 고른 값(TagField→값). 편집중값에 덮어써 저장에 실린다.
    // 커버 피커를 여닫으면 이 시트가 다시 구성되므로, 호스트가 들고 있는 이 값으로 매번 복원한다.
    enrich: Map<TagField, String>? = null,
    onFindCover: () -> Unit,
    // 기기 저장소 사진을 골라 자르기로 커버 지정(온라인 검색과 별개 경로).
    onPickLocal: () -> Unit = {},
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
    // 앨범아트 삭제 토글. 새 커버(replacementArt)가 오면 그게 우선(교체).
    var removeArt by remember { mutableStateOf(false) }

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
            onToggleRemove = { if (replacementArt == null) removeArt = !removeArt },
            currentArt = currentArt,
            replacementArt = replacementArt,
            onFindCover = { removeArt = false; onFindCover() },
            onPickLocal = { removeArt = false; onPickLocal() },
        )
    }
}

/**
 * 편집 시트의 알맹이(앨범아트 헤더 + 필드들). Dialog 밖에서도 그릴 수 있게 분리해, 대조
 * 렌더(스크린샷)와 추후 일괄 편집 시트가 같은 몸을 쓴다.
 */
@Composable
internal fun TagEditContent(
    displayName: String,
    edited: MutableMap<TagField, String>,
    removeArt: Boolean,
    onToggleRemove: () -> Unit,
    currentArt: ByteArray?,
    replacementArt: ByteArray?,
    onFindCover: () -> Unit,
    onPickLocal: () -> Unit = {},
) {
    val c = OloTheme.colors
    Text(displayName, color = c.muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(bottom = 10.dp))

    // 앨범아트: 새로 고른 것 > 지금 심긴 것 > 빈 타일. 커버 찾기 / 삭제.
    Row(verticalAlignment = Alignment.CenterVertically) {
        val model: Any? = replacementArt ?: currentArt?.takeIf { !removeArt }
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(c.accent), contentAlignment = Alignment.Center) {
            if (model != null) {
                AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            } else {
                Icon(Icons.Filled.Album, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text("앨범아트", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallBtn("커버 찾기", primary = true, onClick = onFindCover)
                SmallBtn("기기에서", primary = false, onClick = onPickLocal)
                SmallBtn(if (removeArt && replacementArt == null) "삭제됨" else "삭제", primary = false, onClick = onToggleRemove)
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))

    // 필드. 트랙·디스크·연도는 숫자 키패드.
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
private fun TagRow(field: TagField, edited: MutableMap<TagField, String>, keyboard: KeyboardType = KeyboardType.Text) {
    CpField(
        label = field.label,
        value = edited[field] ?: "",
        onValueChange = { edited[field] = it },
        keyboardType = keyboard,
    )
}

@Composable
private fun SmallBtn(text: String, primary: Boolean, onClick: () -> Unit) {
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
