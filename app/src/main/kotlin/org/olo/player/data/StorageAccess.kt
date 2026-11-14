package org.olo.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * 저장소 접근 공통 헬퍼. 기기 저장소 루트 해석과 '모든 파일 접근' 권한 확인·설정 열기를 한곳에 둬,
 * 여러 화면(파일/폴더 작업·탐색기·설정)과 휴지통이 같은 판정을 쓰도록 한다(예전엔 같은 코드가
 * 파일마다 복사돼 있었다).
 */

/** 기기 외장 저장소 루트. 일부 기기에서 null을 줄 수 있어 통상 경로로 보강한다. */
@Suppress("DEPRECATION")
fun externalStorageRoot(): File = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")

/**
 * 파일을 직접(동의창 없이) 쓸 수 있는가 = API 29 이하(레거시 저장소) 또는 '모든 파일 접근' 허용.
 * 이름변경·이동·복사·삭제·새 폴더가 이 판정으로 갈린다.
 */
fun canManageAllFiles(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

/** '모든 파일 접근' 설정 화면을 연다(앱 지정 화면 → 전체 목록 순으로 폴백). */
fun Context.openAllFilesSettings() {
    val uri = Uri.parse("package:$packageName")
    runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
}
