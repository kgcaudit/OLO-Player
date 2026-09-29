# OLO Player

**OLO Explorer**의 검증된 동영상·음악 재생 기능을 떼어내 고도화하는 별도 안드로이드 앱.

## 시작하기 (새 세션용)

1. **`docs/OLO-Player-Handoff.md`** 를 먼저 정독하라. 재사용할 코어, 결합 지점,
   의존성/매니페스트, 그리고 만들며 얻은 교훈(9가지)이 모두 여기 있다.
2. **`reference/oloexplorer/`** 에 OLO Explorer의 재사용 소스가 원본 그대로 복사돼 있다
   (패키지 `org.filezilla.android.*`). 이식할 때 이 파일들을 옮겨 패키지명을
   `org.olo.player.*` 로 치환하면 된다.

   | 참조 파일 | 역할 |
   |---|---|
   | `playback/PlaybackService.kt` | 재생 엔진(서비스) — 배경재생·알림·오디오포커스·슬립타이머 |
   | `playback/SubtitleBundle.kt` | 자막을 MediaController→세션으로 넘기는 코덱 |
   | `ui/SamiSubtitles.kt` | SAMI(.smi)→WebVTT 변환(MS949/UTF-8) |
   | `ui/MediaViewerScreen.kt` | 화면 전체(음악/영상/설정/제스처/가사) |
   | `ui/FileKind.kt` | `looksMedia/looksVideo` 등 파일종류 판별 |
   | `viewer/TextFiles.kt` | LRC 가사용 텍스트 디코딩 |
   | `ui/MainViewModel.kt`, `data/AppPreferences.kt` | `MediaViewer` 상태·재생위치/자막 prefs의 원본(작은 조각만 발췌 이식) |

3. 문서 7절의 이식 절차대로 앱 골격을 만들고, 1차 목표는
   "로컬 미디어를 배경재생+알림+자막까지 되는 빌드 가능한 골격"이다.

> `reference/` 는 이식이 끝나면 지워도 된다(원본 대조용).
