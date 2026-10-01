# OLO Player — 작업 규칙

## 디자인 변경 루틴 (반드시 지킬 것)
- **모든 수정/디자인 지시는 먼저 구상안(목업)을 이미지로 렌더해 사용자에게 보여준 뒤 진행한다.**
  사용자가 "구상안 이미지는 필요 없다"고 명시할 때만 생략한다.
- 워크플로: 구상안(이미지) → 의사결정(가/나 + 권고) → 확정 → 코딩 → 대조(실제 앱 렌더 vs 확정 구상안).
- 구상안·대조는 Robolectric NATIVE 스크린샷 하니스로 실제 컴포넌트를 렌더한다
  (`@GraphicsMode(NATIVE)`, `@Config(qualifiers="w467dp-h748dp-xhdpi")` 커버 /
  `"w969dp-h732dp-xhdpi"` 펼침). 목업/스크린샷 코드는 **커밋하지 않는다**(스크래치패드 보관).
- 폴더블 2규격 반응형: 커버(≈467×748dp) / 펼침(≈969×732dp) 모두 만족.

## 소통·저장소
- 모든 문구·커밋·주석은 **한국어**("무엇이 아니라 왜"를 설명).
- 작업 브랜치는 **`claude/followup`**. PR은 명시적으로 요청할 때만.
- 커밋 메시지에 모델 식별자 금지. 호스트키·인증서 검증 우회 금지, 비밀번호 평문 로그 금지,
  TMDB 키 커밋 금지(BuildConfig/local.properties).

## 아이콘·테마 공유 소스 (OLO-Design)
- 심볼·아이콘·테마(색/타입/셰이프)·FileKind는 `kgcaudit/OLO-Design`(main)이 공유 소스다.
  **테마/아이콘을 손볼 때만** `add_repo kgcaudit/OLO-Design`(read) 후 `docs/CONSUMING.md`
  1·2·3단계대로 복사/빌드한다(테마 + 타일/글리프 + FileKind). 런처 아이콘은
  `python3 appicons/launcher.py --app player --out <앱>/app/src/main/res/drawable`
  (마크=재생 삼각형, 공유 클레이 바탕) + `appicons/mipmap/*.xml` → `mipmap-anydpi-v26/`.
- `icons/FILEKIND.md`의 FileKind 집합·확장자·kind→hue/glyph 맵은 전 앱 동일하게 유지.
- 하드 제약: Material-You/동적 색상 금지 · 타일은 hue로 구분 · two-tone 흰 글리프는
  tint=Unspecified. **baseline(hex/radius/type/hue) 값은 앱에서 고치지 말고 OLO-Design에
  먼저 반영**한다. 지금 생성 드로어블은 shipping본과 동일(검증됨)이라 강제 재동기화 불필요.

## 검증
- 변경 시 `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug --rerun-tasks` 통과 확인.
- 신규 로직은 가능하면 유닛 테스트로 고정.
- 네트워크(FTP/SFTP/SMB/WebDAV) 실동작·플레이어 자막 렌더는 이 환경에서 검증 불가 →
  실기기 확인이 필요함을 사용자에게 명시.
