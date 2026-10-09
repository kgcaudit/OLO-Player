package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.art.ArtworkEdit
import org.olo.player.art.FieldEdit
import org.olo.player.art.TagEdit
import org.olo.player.art.TagField
import org.olo.player.art.TrackTags
import org.olo.player.ui.components.CpField
import org.olo.player.ui.theme.OloTheme

/**
 * 여러 곡 일괄 태그 편집(Mp3tag 다중편집 형). 선택한 곡들의 값이 모두 같으면 그 값을, 다르면
 * '〈유지〉'를 보여 주고, 체크(또는 값 입력)한 필드만 전체에 적용한다. 체크 안 한 필드는 손대지
 * 않는다(Keep). 앨범아트는 커버 피커에서 한 장을 골라 전체에 일괄 적용할 수 있다.
 */
@Composable
fun BatchTagEditSheet(
    tracks: List<TrackTags>,
    replacementArt: ByteArray? = null,
    onFindCover: () -> Unit,
    onSave: (edits: Map<TagField, FieldEdit>, artwork: ArtworkEdit?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 공통값(없으면 ""=〈유지〉 표시), 적용 체크. 값 입력하면 자동으로 적용 켜짐.
    val value = remember(tracks) {
        mutableStateMapOf<TagField, String>().apply {
            TagField.entries.forEach { put(it, TagEdit.commonValue(tracks, it) ?: "") }
        }
    }
    val applied = remember(tracks) { mutableStateMapOf<TagField, Boolean>() }

    OloCardDialog(
        title = "일괄 태그 편집 · ${tracks.size}곡",
        onDismiss = onDismiss,
        actions = {
            OloDialogButton("취소", onClick = onDismiss, primary = false)
            OloDialogButton("저장", onClick = {
                val edits = TagField.entries
                    .filter { applied[it] == true }
                    .associateWith { FieldEdit.Set(value[it] ?: "") }
                val art = replacementArt?.let { ArtworkEdit.Set(it) }
                onSave(edits, art)
            })
        },
    ) {
        BatchTagEditContent(tracks.size, value, applied, replacementArt != null, onFindCover)
    }
}

/** 알맹이: 앨범아트 일괄 + 필드별 〈유지〉/적용. Dialog 밖에서도 그릴 수 있게 분리(대조 렌더용). */
@Composable
internal fun BatchTagEditContent(
    count: Int,
    value: MutableMap<TagField, String>,
    applied: MutableMap<TagField, Boolean>,
    hasCover: Boolean,
    onFindCover: () -> Unit,
) {
    val c = OloTheme.colors
    Text("서로 다른 값은 ‘〈유지〉’. 체크하거나 값을 넣은 필드만 ${count}곡 전체에 적용됩니다.",
        color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (hasCover) "앨범아트: 고른 커버를 전체 적용" else "앨범아트", color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box(Modifier.background(c.accent, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable(onClick = onFindCover).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("커버 찾기", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))

    BatchRow(TagField.TITLE, value, applied)
    BatchRow(TagField.ARTIST, value, applied)
    BatchRow(TagField.ALBUM, value, applied)
    BatchRow(TagField.ALBUM_ARTIST, value, applied)
    BatchRow(TagField.DISC, value, applied, KeyboardType.Number)
    BatchRow(TagField.YEAR, value, applied, KeyboardType.Number)
    BatchRow(TagField.GENRE, value, applied)
    BatchRow(TagField.COMPOSER, value, applied)
    BatchRow(TagField.COMMENT, value, applied)
    // 트랙번호는 곡마다 달라 일괄 '같은 값'이 의미가 적어 뺀다(번호매기기는 추후 도구로).
}

@Composable
private fun BatchRow(
    field: TagField,
    value: MutableMap<TagField, String>,
    applied: MutableMap<TagField, Boolean>,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val c = OloTheme.colors
    val on = applied[field] == true
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (on) "적용" else "유지",
            tint = if (on) c.accent else c.muted,
            modifier = Modifier.size(20.dp).clickable { applied[field] = !on },
        )
        Spacer(Modifier.size(8.dp))
        Box(Modifier.weight(1f)) {
            CpField(
                label = field.label,
                value = value[field] ?: "",
                onValueChange = { value[field] = it; applied[field] = true },
                placeholder = "〈유지〉",
                keyboardType = keyboard,
            )
        }
    }
}
