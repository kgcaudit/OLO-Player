package org.olo.player.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable

/**
 * 앨범아트/가사 하위 작업 시트. 편집기엔 '커버 ›' '가사 ›' 버튼만 두고, 세부 작업(찾기·기기에서·
 * 삭제)은 이 시트에 모은다 -- 편집 공간을 심플·체계적으로 유지하기 위함(사용자 요청).
 *
 * 저장·권한·네트워크는 호스트가 콜백으로 처리한다. 이 파일은 표현·분기만 담당한다.
 */

/**
 * 앨범아트 작업 시트: 온라인에서 찾기 · 기기에서 선택 · 삭제. [hasArt]가 없으면 삭제는 흐리게.
 */
@Composable
fun AlbumArtActionSheet(
    hasArt: Boolean,
    onFind: () -> Unit,
    onPickLocal: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    OloCardDialog(title = "앨범아트", onDismiss = onDismiss, dismissLabel = "닫기") {
        AlbumArtActionBody(hasArt, onFind, onPickLocal, onRemove)
    }
}

/** 앨범아트 시트 알맹이(대조 렌더가 다이얼로그 밖에서도 같은 몸을 쓰도록 분리). */
@Composable
internal fun AlbumArtActionBody(hasArt: Boolean, onFind: () -> Unit, onPickLocal: () -> Unit, onRemove: () -> Unit) {
    OloActionRow(Icons.Filled.Search, "온라인에서 찾기", "iTunes · MusicBrainz 커버 검색", onClick = onFind)
    OloActionRow(Icons.Filled.PhotoLibrary, "기기에서 선택", "사진을 정사각으로 잘라 지정", onClick = onPickLocal)
    OloActionRow(Icons.Filled.Delete, "앨범아트 삭제", "파일에 심긴 커버 제거", danger = true, enabled = hasArt, onClick = onRemove)
}

/**
 * 가사 작업 시트: 가사 찾기 · 기기에서(.lrc 불러오기) · 삭제. [hasLyrics]가 없으면 삭제는 흐리게.
 */
@Composable
fun LyricsActionSheet(
    hasLyrics: Boolean,
    onFind: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    OloCardDialog(title = "가사", onDismiss = onDismiss, dismissLabel = "닫기") {
        LyricsActionBody(hasLyrics, onFind, onImport, onRemove)
    }
}

/** 가사 시트 알맹이(대조 렌더용 분리). */
@Composable
internal fun LyricsActionBody(hasLyrics: Boolean, onFind: () -> Unit, onImport: () -> Unit, onRemove: () -> Unit) {
    OloActionRow(Icons.Filled.Search, "가사 찾기", "LRCLIB 검색 · 동기/일반 저장", onClick = onFind)
    OloActionRow(Icons.Filled.Description, "기기에서 (.lrc)", "저장된 가사 파일 불러오기", onClick = onImport)
    OloActionRow(Icons.Filled.Delete, "저장된 가사 삭제", ".lrc · 앱 보관 제거", danger = true, enabled = hasLyrics, onClick = onRemove)
}
