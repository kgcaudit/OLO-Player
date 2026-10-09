package org.olo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.art.SourceMeta
import org.olo.player.art.TagField
import org.olo.player.art.TrackTags
import org.olo.player.ui.theme.OloTheme

// 보강 여백색/강조: 제안값(채움)은 초록 계열로, 같은 값은 흐리게.
private val OK_TINT = Color(0xFF3E7D5A)

/**
 * 메타데이터 보강 리뷰 시트. 고른 커버의 출처 메타([source])와 현재 태그([current])를 필드별로
 * 대조해(현재값 → 제안값) 적용할 칸을 고른다. 기본 정책은 '보강'(빈 칸만 자동 체크) -- 사용자가
 * 넣은 값은 건드리지 않는다. '모두 덮어쓰기'로 바꾸면 같은 값을 뺀 모든 제안을 체크한다.
 *
 * 적용은 바로 쓰지 않는다. 고른 칸을 TagField→값 맵으로 [onApply]에 넘기면 호출부가 편집 시트에
 * 담아, 저장 전 사용자가 다시 확인/수정하게 한다(출처가 틀릴 수도 있으니 마지막 결정은 사람).
 */
@Composable
fun MetaEnrichSheet(
    source: SourceMeta,
    current: TrackTags,
    onApply: (Map<TagField, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // 제안 가능한 필드(출처가 준 것) → 제안값. 현재값과 같은 건 '동일'로 표시하고 체크 대상에서 뺀다.
    val suggestions = remember(source) { source.toFields() }
    val rows = remember(source, current) {
        suggestions.entries.map { (f, sug) ->
            val cur = current[f]?.takeIf { it.isNotBlank() }
            EnrichRow(f, cur, sug, same = cur == sug)
        }
    }
    // 덮어쓰기 토글(기본 false=보강). 체크 상태는 필드별로 사용자가 바꿀 수 있다.
    var overwrite by remember { mutableStateOf(false) }
    val checked = remember(source, current) {
        mutableStateMapOf<TagField, Boolean>().apply {
            rows.forEach { put(it.field, defaultChecked(it, overwrite = false)) }
        }
    }
    // 정책 토글을 바꾸면 '동일이 아닌' 행의 기본 체크를 다시 깐다(사용자가 개별로 바꾼 건 덮어쓴다).
    fun applyPolicy(ow: Boolean) {
        overwrite = ow
        rows.forEach { checked[it.field] = defaultChecked(it, ow) }
    }

    OloCardDialog(
        title = "메타데이터 보강",
        onDismiss = onDismiss,
        titleAccent = true,
        actions = {
            OloDialogButton("건너뛰기", onClick = onDismiss, primary = false)
            OloDialogButton("보강 적용", onClick = {
                val picked = rows.filter { checked[it.field] == true }.associate { it.field to it.suggested }
                onApply(picked)
            })
        },
    ) {
        MetaEnrichContent(source, rows, checked, overwrite, onToggle = { f -> checked[f] = !(checked[f] ?: false) }, onPolicy = { applyPolicy(it) })
    }
}

/** 한 보강 행: 필드, 현재값(없으면 null), 제안값, 동일 여부. */
internal data class EnrichRow(val field: TagField, val current: String?, val suggested: String, val same: Boolean)

// 기본 체크: 동일값은 끔. 보강(빈 칸만)=현재값이 없을 때만 켬. 덮어쓰기=동일이 아니면 켬.
private fun defaultChecked(row: EnrichRow, overwrite: Boolean): Boolean =
    !row.same && (overwrite || row.current == null)

/** 시트 알맹이(출처 배지 + 정책 토글 + 대조 표). Dialog 밖에서도 그릴 수 있게 분리(대조 렌더용). */
@Composable
internal fun MetaEnrichContent(
    source: SourceMeta,
    rows: List<EnrichRow>,
    checked: Map<TagField, Boolean>,
    overwrite: Boolean,
    onToggle: (TagField) -> Unit,
    onPolicy: (Boolean) -> Unit,
) {
    val c = OloTheme.colors
    // 출처 요약: 아티스트 — 앨범(있으면). 어디서 온 제안인지 한 줄로.
    val srcLine = listOfNotNull(source.artist, source.album).joinToString(" — ").ifBlank { "검색 결과" }
    Text("출처 정보로 빈 칸을 채웁니다", color = c.muted, fontSize = 12.sp)
    Text(srcLine, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PolicyChip("빈 칸만 채우기", on = !overwrite) { onPolicy(false) }
        PolicyChip("모두 덮어쓰기", on = overwrite) { onPolicy(true) }
    }

    Spacer(Modifier.height(12.dp))
    if (rows.isEmpty()) {
        Text("이 출처에서 채울 수 있는 정보가 없습니다.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    // 헤더
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Spacer(Modifier.width(28.dp))
        Text("필드", color = c.muted, fontSize = 10.sp, modifier = Modifier.width(76.dp))
        Text("현재값", color = c.muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text("제안값", color = c.muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
    }
    rows.forEach { row ->
        val on = checked[row.field] == true
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !row.same) { onToggle(row.field) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(5.dp))
                    .background(if (on) c.accent else Color.Transparent)
                    .border(1.5.dp, if (on) c.accent else c.outline, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(row.field.label, color = c.text, fontSize = 12.sp, modifier = Modifier.width(76.dp))
            Text(row.current ?: "〈비어 있음〉", color = if (row.current == null) c.muted.copy(alpha = 0.7f) else c.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(row.suggested, color = if (row.same) c.muted else OK_TINT, fontSize = 12.sp, fontWeight = if (!row.same && row.current == null) FontWeight.Medium else FontWeight.Normal)
                if (row.same) { Spacer(Modifier.width(6.dp)); Text("· 동일", color = c.muted, fontSize = 9.sp) }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("적용은 편집 시트에 담깁니다 — 저장 전에 다시 확인·수정할 수 있어요.", color = c.muted, fontSize = 10.sp, lineHeight = 14.sp)
}

@Composable
private fun PolicyChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = OloTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) c.accent else Color.Transparent)
            .border(1.dp, if (on) c.accent else c.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
    ) { Text(text, color = if (on) Color.White else c.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
}
