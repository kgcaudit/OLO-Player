package org.olo.player.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.art.JAudioTagWriter
import org.olo.player.art.LyricsClient
import org.olo.player.art.LyricsHit
import org.olo.player.art.LyricsParser
import org.olo.player.art.LyricsStore
import org.olo.player.art.MediaTagSaver
import org.olo.player.art.TagWriteResult
import org.olo.player.ui.theme.OloTheme

/**
 * 가사 찾기·저장 흐름을 묶는 호스트. LRCLIB에서 후보를 받아 컴팩트 목록으로 보이고, 고르면
 * 세 자리에 저장한다: 곡 옆 .lrc(가능 시) + 파일 태그 임베드(API 30+는 동의) + 앱 보관(항상).
 * 저장 뒤 [onSaved]로 가사를 다시 읽게 한다. 결과를 한 줄로 알리고 [onClose]로 닫는다.
 *
 * 네트워크·파일 쓰기는 IO에서 돈다. 실제 권한·쓰기·임베드는 기기에서만 검증된다.
 */
@Composable
fun LyricsSaveHost(
    entry: MediaEntry,
    artist: String,
    title: String,
    album: String,
    durationSec: Int,
    onSaved: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val local = entry.localFile

    var hits by remember { mutableStateOf<List<LyricsHit>?>(null) } // null=검색 중
    var busy by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var pendingText by remember { mutableStateOf<String?>(null) }
    var sidecarDone by remember { mutableStateOf(false) }

    // 태그 임베드 동의(API 30+). 동의되면 쌓아 둔 가사를 파일에 쓴다. 거부해도 .lrc·앱 보관은 이미 됨.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val text = pendingText
        pendingText = null
        if (res.resultCode == Activity.RESULT_OK && text != null && local != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) {
                    MediaTagSaver.applyLyricsToFile(local, text).also { MediaTagSaver.rescan(context, listOf(local)) }
                }
                resultMsg = savedSummary(sidecarDone, embedded = r is TagWriteResult.Ok, embedFail = (r as? TagWriteResult.Failed)?.reason)
                onSaved()
            }
        } else {
            resultMsg = if (sidecarDone) "가사를 곡 옆 .lrc와 앱에 저장했습니다(파일 임베드는 동의 필요)."
            else "가사를 앱에 저장했습니다(파일 임베드는 동의 필요)."
            onSaved()
        }
    }

    LaunchedEffect(Unit) {
        hits = withContext(Dispatchers.IO) {
            runCatching { LyricsClient().search(artist, title, album, durationSec) }.getOrElse { emptyList() }
        }
    }

    fun save(hit: LyricsHit) {
        val text = hit.best ?: return
        busy = true
        scope.launch {
            // ① 앱 보관(항상) + ② 사이드카(폴더에 쓸 수 있을 때).
            sidecarDone = withContext(Dispatchers.IO) {
                LyricsStore.writeAppStore(context, entry.prefKey, text)
                local?.let { LyricsStore.writeSidecarIfPossible(it, text) } ?: false
            }
            // ③ 태그 임베드. API 30+ 공유 저장소 파일은 동의를 받아야 하므로 런처로 넘긴다.
            if (local != null && JAudioTagWriter.supports(local)) {
                if (MediaTagSaver.needsWriteRequest) {
                    val uri = withContext(Dispatchers.IO) { MediaTagSaver.audioUri(context, local) }
                    if (uri != null) {
                        pendingText = text
                        runCatching {
                            permLauncher.launch(
                                IntentSenderRequest.Builder(MediaTagSaver.writeRequest(context, listOf(uri))).build(),
                            )
                        }.onFailure {
                            resultMsg = savedSummary(sidecarDone, embedded = false, embedFail = "권한 요청 실패: ${it.message}")
                            onSaved()
                        }
                        busy = false
                        return@launch
                    }
                }
                val r = withContext(Dispatchers.IO) {
                    MediaTagSaver.applyLyricsToFile(local, text).also { MediaTagSaver.rescan(context, listOf(local)) }
                }
                resultMsg = savedSummary(sidecarDone, embedded = r is TagWriteResult.Ok, embedFail = (r as? TagWriteResult.Failed)?.reason)
            } else {
                // 원격·미지원 포맷: 앱 보관만(사이드카는 로컬·쓰기 가능할 때만 됐을 수 있음).
                resultMsg = if (sidecarDone) "가사를 곡 옆 .lrc와 앱에 저장했습니다."
                else "가사를 앱에 저장했습니다(원격·미지원 포맷은 파일에 담지 못함)."
            }
            busy = false
            onSaved()
        }
    }

    val c = OloTheme.colors
    if (resultMsg != null) {
        OloCardDialog(title = "가사 저장", onDismiss = onClose, dismissLabel = "확인") {
            Text(resultMsg!!, color = c.text, fontSize = 13.sp)
        }
        return
    }

    OloCardDialog(
        title = "가사 찾기",
        onDismiss = onClose,
        titleAccent = true,
        actions = { OloDialogButton("닫기", onClick = onClose) },
    ) {
        LyricsFindBody(title, artist, durationSec, hits, busy, onSave = ::save)
    }
}

/**
 * 찾기 시트 알맹이(조회 대상 요약 · 후보 목록 · 저장 안내). 다이얼로그 밖에서도 그릴 수 있게 분리해
 * 대조 렌더가 같은 몸을 쓴다. [hits]가 null이면 검색 중, 빈 목록이면 없음.
 */
@Composable
internal fun LyricsFindBody(
    title: String,
    artist: String,
    durationSec: Int,
    hits: List<LyricsHit>?,
    busy: Boolean,
    onSave: (LyricsHit) -> Unit,
) {
    val c = OloTheme.colors
    val durLabel = if (durationSec > 0) " · ${mmss(durationSec)} 기준" else ""
    Text(
        listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "현재 곡" } + durLabel,
        color = c.muted, fontSize = 10.sp,
    )
    Spacer(Modifier.height(10.dp))
    when (hits) {
        null -> Text("LRCLIB에서 찾는 중…", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        else -> if (hits.isEmpty()) {
            Text("일치하는 가사를 찾지 못했습니다. 제목·아티스트 태그를 확인해 주세요.", color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hits.forEach { hit -> HitRow(hit, durationSec, busy) { onSave(hit) } }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(".lrc를 곡 옆에(가능 시) + 파일 태그에 저장 · 원격은 앱에 보관", color = c.muted, fontSize = 9.sp, lineHeight = 13.sp)
}

@Composable
private fun HitRow(hit: LyricsHit, songDurSec: Int, busy: Boolean, onSave: () -> Unit) {
    val c = OloTheme.colors
    val preview = remember(hit) { previewOf(hit) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).padding(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(if (hit.hasSynced) "동기" else "일반", hit.hasSynced)
            Spacer(Modifier.width(6.dp))
            Text(matchLabel(hit.durationSec, songDurSec), color = c.muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (busy) c.outline else c.accent)
                    .clickable(enabled = !busy, onClick = onSave)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) { Text("저장", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        if (preview.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(preview, color = c.text.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 15.sp, maxLines = 2)
        }
    }
}

@Composable
private fun Badge(text: String, on: Boolean) {
    val c = OloTheme.colors
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) c.accent else Color.Transparent)
        .border(1.dp, if (on) c.accent else c.outline, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = if (on) Color.White else c.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// 후보 미리보기: 동기면 타임스탬프를 걷어내고 가사 첫 두 줄.
private fun previewOf(hit: LyricsHit): String {
    val lyrics = LyricsParser.parse(hit.best ?: return "")
    return lyrics.lines.map { it.text }.filter { it.isNotBlank() }.take(2).joinToString("\n")
}

// 길이 매칭 라벨: 곡 길이와 비교해 정확/근사/표시만.
private fun matchLabel(hitSec: Int, songSec: Int): String {
    if (hitSec <= 0) return "LRCLIB"
    val base = "길이 ${mmss(hitSec)}"
    if (songSec <= 0) return base
    val d = abs(hitSec - songSec)
    return base + when {
        d <= 1 -> " (정확 일치)"
        d <= 3 -> " (±${d}초)"
        else -> " (차이 ${d}초)"
    }
}

private fun mmss(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

private fun savedSummary(sidecar: Boolean, embedded: Boolean, embedFail: String?): String = buildString {
    append("가사를 저장했습니다 · 앱 보관")
    if (sidecar) append(" · 곡 옆 .lrc")
    if (embedded) append(" · 파일 태그") else if (embedFail != null) append(" (태그 임베드 실패: $embedFail)")
}
