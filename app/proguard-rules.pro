# OLO Player R8 규칙.
#
# 왜 이 규칙들이 필요한가: R8은 "코드에서 직접 참조되는" 클래스만 남긴다. 그런데
# 네트워크 암호 라이브러리들은 알고리즘 구현을 "문자열 이름 → 반사(Class.forName)"로
# 올리므로, 참조가 안 보여 R8이 지워버리면 연결이 런타임에 조용히 깨진다. 아래는 그
# 반사 경로에 걸리는 라이브러리만 콕 집어 보존한다. material-icons-extended·Compose·
# 미사용 stdlib 등 반사와 무관한 것은 일부러 keep하지 않아 R8이 제거하도록 둔다
# (APK 용량 감소의 핵심).

# --- JSch(mwiede): SFTP. 암호·KEX·서명 구현을 com.jcraft.jsch.{jce,bc,jgss}.* 에서
#     설정 문자열로 반사 로딩한다. 통째로 보존하지 않으면 접속/핸드셰이크가 깨진다.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# --- BouncyCastle: smbj(SMB3 암호)·jsch가 쓰는 JCE Provider. Provider와 알고리즘
#     SPI가 java.security 서비스+반사로 등록되므로 보존한다.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# --- smbj: SMB/CIFS. 다이얼렉트·인증 팩토리를 일부 반사로 올리고, 선택적 로거
#     (slf4j)를 참조한다. 안전하게 보존하고 선택 의존성 경고는 끈다.
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**
-dontwarn org.slf4j.**

# smbj의 이벤트버스(mbassador)가 선택적으로 참조하는 표현식 언어(javax.el). Android엔
# 없고 쓰지도 않는 경로라 경고만 끈다(해당 코드는 실행되지 않음).
-dontwarn javax.el.**

# commons-net(FTP)·media3·Coil·coroutines는 반사를 쓰지 않거나 자체 consumer 규칙을
# AAR에 동봉하므로 별도 keep이 필요 없다. 혹시 모를 선택 의존성 경고만 끈다.
-dontwarn javax.annotation.**
-dontwarn org.ietf.jgss.**

# --- jaudiotagger(태그 편집): 태그 리더/라이터·프레임 클래스를 포맷·프레임ID 문자열로
#     반사 로딩하고, 언어/장르 등 리소스 번들을 패키지 경로로 읽는다. R8이 지우면 태그
#     읽기/쓰기가 런타임에 깨지므로 통째로 보존한다. java.awt/ImageIO 참조(Android엔
#     없으나 앨범아트 경로가 AndroidArtwork로 갈려 실행되지 않음)는 경고만 끈다.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
