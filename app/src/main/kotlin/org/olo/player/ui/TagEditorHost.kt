package org.olo.player.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.art.AlbumArtClient
import org.olo.player.art.ArtworkEdit
import org.olo.player.art.FieldEdit
import org.olo.player.art.MediaTagSaver
import org.olo.player.art.TagField
import org.olo.player.art.TagReader
import org.olo.player.art.TagWriteResult
import org.olo.player.art.TrackTags

/**
 * 태그 편집 전체 흐름을 묶는 호스트. 로컬 음악 [files](1곡=단일, 여러 곡=일괄)를 받아 태그를 읽고,
 * 편집 시트 ↔ 커버 피커를 오가며, 저장 때 저장소 권한(API 30+ createWriteRequest)→쓰기→MediaStore
 * 재스캔까지 처리한다. 결과는 간단한 요약으로 알리고 [onClose]로 닫는다.
 *
 * 네트워크 파일은 호출부가 거른다(로컬 File만 넘긴다). 실제 권한·쓰기·재스캔은 기기에서만 검증된다.
 */
@Composable
fun TagEditorHost(files: List<File>, onClose: () -> Unit) {
    if (files.isEmpty()) {
        onClose(); return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val batch = files.size > 1

    // 로드 상태.
    var loaded by remember { mutableStateOf(false) }
    var singleTags by remember { mutableStateOf(TrackTags()) }
    var singleArt by remember { mutableStateOf<ByteArray?>(null) }
    var batchTags by remember { mutableStateOf<List<TrackTags>>(emptyList()) }

    // 화면 전환·선택 상태.
    var showCover by remember { mutableStateOf(false) }
    var replacementArt by remember { mutableStateOf<ByteArray?>(null) }
    var pending by remember { mutableStateOf<Pair<Map<TagField, FieldEdit>, ArtworkEdit?>?>(null) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    // 메타데이터 보강: 고른 커버의 출처 메타로 띄울 리뷰 시트와, 사용자가 고른 보강 값(편집 시트에
    // 반영). 단일 파일 편집에서만 쓴다(곡 단위 제목·트랙이 섞이는 일괄엔 적용하지 않음).
    var enrichMeta by remember { mutableStateOf<org.olo.player.art.SourceMeta?>(null) }
    var enrichApplied by remember { mutableStateOf<Map<TagField, String>?>(null) }

    // 저장 동의(API 30+) 런처. 동의되면 쌓아 둔 pending 편집을 실제로 쓴다.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val p = pending
        if (res.resultCode == Activity.RESULT_OK && p != null) {
            scope.launch { performWrite(context, files, p.first, p.second) { resultMsg = it } }
        } else {
            resultMsg = "저장이 취소되었습니다(원본 그대로)."
        }
    }

    LaunchedEffect(files) {
        withContext(Dispatchers.IO) {
            if (batch) {
                batchTags = files.map { TagReader.read(it) }
            } else {
                singleTags = TagReader.read(files.first())
                singleArt = TagReader.readArtwork(files.first())
            }
        }
        loaded = true
    }

    // 저장 요청: 권한이 필요하면 동의를 받고, 아니면 바로 쓴다.
    fun requestSave(edits: Map<TagField, FieldEdit>, artwork: ArtworkEdit?) {
        if (edits.isEmpty() && artwork == null) { onClose(); return }
        pending = edits to artwork
        if (MediaTagSaver.needsWriteRequest) {
            val uris = files.mapNotNull { MediaTagSaver.audioUri(context, it) }
            if (uris.isEmpty()) { // MediaStore에 없으면 레거시 경로로 시도.
                scope.launch { performWrite(context, files, edits, artwork) { resultMsg = it } }
            } else {
                runCatching {
                    permLauncher.launch(IntentSenderRequest.Builder(MediaTagSaver.writeRequest(context, uris)).build())
                }.onFailure { resultMsg = "권한 요청 실패: ${it.message}" }
            }
        } else {
            scope.launch { performWrite(context, files, edits, artwork) { resultMsg = it } }
        }
    }

    val defaultCountry = remember { (Locale.getDefault().country.ifBlank { "KR" }) }

    when {
        resultMsg != null -> OloCardDialog(title = "태그 저장", onDismiss = onClose, dismissLabel = "확인") {
            androidx.compose.material3.Text(resultMsg!!, color = org.olo.player.ui.theme.OloTheme.colors.text)
        }
        !loaded -> OloCardDialog(title = "태그 편집", onDismiss = onClose, dismissLabel = "닫기") {
            androidx.compose.material3.Text("불러오는 중…", color = org.olo.player.ui.theme.OloTheme.colors.muted)
        }
        showCover -> {
            val seed = if (batch) commonSeed(batchTags) else singleTags
            // 노래 제목 씨앗: 단일 편집은 태그 제목 -> 없으면 파일명(확장자 제거). 일괄은 곡마다 달라
            // 비워 둔다(사용자가 필요 시 입력). EP·싱글 커버를 곡 단위로 찾는 데 쓴다.
            val titleSeed = if (batch) "" else
                singleTags[TagField.TITLE].orEmpty().ifBlank { files.first().nameWithoutExtension }
            CoverPickerSheet(
                initialArtist = seed[TagField.ARTIST].orEmpty(),
                initialAlbum = seed[TagField.ALBUM].orEmpty(),
                initialTitle = titleSeed,
                defaultCountry = defaultCountry,
                search = { artist, album, title, country -> AlbumArtClient(country = country).searchFlow(artist, album, title) },
                loadBytes = { url -> downloadBytes(url) },
                onPicked = { bytes, meta ->
                    replacementArt = bytes
                    showCover = false
                    // 단일 파일이고 출처 메타가 있으면 보강 리뷰로. 일괄은 커버만 바꾼다.
                    enrichMeta = if (!batch && meta != null && !meta.isEmpty()) meta else null
                },
                onDismiss = { showCover = false },
            )
        }
        // 보강 리뷰(단일 파일): 고른 값은 편집 시트의 편집중값에 누적 반영된다.
        enrichMeta != null && !batch -> MetaEnrichSheet(
            source = enrichMeta!!,
            current = singleTags,
            onApply = { picked ->
                enrichApplied = (enrichApplied ?: emptyMap()) + picked
                enrichMeta = null
            },
            onDismiss = { enrichMeta = null },
        )
        batch -> BatchTagEditSheet(
            tracks = batchTags,
            replacementArt = replacementArt,
            onFindCover = { showCover = true },
            onSave = { edits, art -> requestSave(edits, art) },
            onDismiss = onClose,
        )
        else -> TagEditSheet(
            displayName = files.first().name,
            tags = singleTags,
            currentArt = singleArt,
            replacementArt = replacementArt,
            enrich = enrichApplied,
            onFindCover = { showCover = true },
            onSave = { edits, art -> requestSave(edits, art) },
            onDismiss = onClose,
        )
    }
}

// 일괄의 커버 검색 기본값: 공통 아티스트/앨범(다르면 빈칸).
private fun commonSeed(tracks: List<TrackTags>): TrackTags = TrackTags(
    buildMap {
        org.olo.player.art.TagEdit.commonValue(tracks, TagField.ARTIST)?.let { put(TagField.ARTIST, it) }
        org.olo.player.art.TagEdit.commonValue(tracks, TagField.ALBUM)?.let { put(TagField.ALBUM, it) }
    },
)

// 실제 쓰기 + 재스캔. 결과를 '성공 N · 실패 M'로 요약한다. IO에서 돈다.
private suspend fun performWrite(
    context: android.content.Context,
    files: List<File>,
    edits: Map<TagField, FieldEdit>,
    artwork: ArtworkEdit?,
    onResult: (String) -> Unit,
) {
    val (ok, failed) = withContext(Dispatchers.IO) {
        var ok = 0; val fails = ArrayList<String>()
        for (f in files) {
            val r = if (MediaTagSaver.needsWriteRequest) {
                val uri = MediaTagSaver.audioUri(context, f)
                if (uri != null) MediaTagSaver.applyToUri(context, uri, f.name, edits, artwork)
                else MediaTagSaver.applyToFile(f, edits, artwork)
            } else {
                MediaTagSaver.applyToFile(f, edits, artwork)
            }
            when (r) {
                is TagWriteResult.Ok -> ok++
                is TagWriteResult.Unsupported -> fails.add("${f.name}: 미지원 포맷")
                is TagWriteResult.Failed -> fails.add("${f.name}: ${r.reason}")
            }
        }
        MediaTagSaver.rescan(context, files)
        ok to fails
    }
    val msg = buildString {
        append("저장 완료 · 성공 ${ok}곡")
        if (failed.isNotEmpty()) append(" · 실패 ${failed.size}곡\n").append(failed.take(5).joinToString("\n"))
    }
    withContext(Dispatchers.Main) { onResult(msg) }
}

// 고른 커버의 원본 바이트를 받는다(블로킹, IO에서 호출). 실패는 null.
private fun downloadBytes(url: String): ByteArray? = runCatching {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 15_000
    conn.instanceFollowRedirects = true
    try {
        if (conn.responseCode !in 200..299) return null
        conn.inputStream.use { it.readBytes() }
    } finally {
        conn.disconnect()
    }
}.getOrNull()
