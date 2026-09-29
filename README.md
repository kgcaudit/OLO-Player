# OLO Player

**OLO Explorer**의 검증된 동영상·음악 재생 기능을 떼어내 고도화하는 별도 안드로이드 앱.

## 현황

미디어 코어 이식이 끝나 **빌드 가능한 1차 골격**이 올라와 있다. 로컬 동영상/음악을
배경재생 + 알림/잠금화면 컨트롤 + 자막(내장·외부, SAMI 포함)까지 재생한다.

- 패키지: `org.olo.player`, minSdk 26 / target·compileSdk 35
- 미디어 엔진: AndroidX media3(ExoPlayer) 1.5.1, UI: Kotlin + Jetpack Compose(Material3)
- 빌드: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## 구조

```
app/src/main/kotlin/org/olo/player/
  MainActivity.kt              앱 진입 — 파일 선택 ↔ 플레이어 전환
  playback/PlaybackService.kt  재생 엔진(서비스): 배경재생·알림·오디오포커스·슬립타이머
  playback/SubtitleBundle.kt   자막을 MediaController→세션으로 넘기는 코덱
  ui/MediaViewerScreen.kt      화면 전체(음악/영상/설정/제스처/가사)
  ui/SamiSubtitles.kt          SAMI(.smi)→WebVTT 변환(MS949/UTF-8)
  ui/FileKind.kt               looksMedia/looksVideo 등 파일종류 판별
  ui/PlayerViewModel.kt        MediaViewer 상태 + 폴더 형제 재생목록 구성
  ui/FilePickerScreen.kt       진입 화면(파일 탐색기)
  ui/theme/Theme.kt            Material3 테마
  data/AppPreferences.kt       파일별 재생위치·자막선택(LRU) + 자막 크기/색
  viewer/TextFiles.kt          LRC 가사용 텍스트 디코딩(UTF-8/CP949)
```

## 배경 지식

이식의 배경과 만들며 얻은 교훈(9가지), 의존성/매니페스트 근거는
**`docs/OLO-Player-Handoff.md`** 에 정리돼 있다. 고도화 로드맵도 그 문서 9절 참고.

> 이식이 끝나 원본 참조용 `reference/oloexplorer/`(패키지 `org.filezilla.android.*`)는
> 제거했다. 필요하면 git 이력이나 OLO Explorer 저장소에서 되찾을 수 있다.
