import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The default TMDB API key ships from local.properties (tmdb.apiKey=...), which is
// git-ignored, so the key never enters the repository. Absent, the field is empty
// and posters simply stay off until a key is entered in settings.
val tmdbApiKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("tmdb.apiKey", "")

android {
    namespace = "org.olo.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.olo.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }

    buildTypes {
        release {
            // 코드 축소(R8)와 리소스 축소를 켠다. 디버그 APK가 큰 주원인은 쓰지도 않는
            // 라이브러리 클래스(특히 material-icons-extended 수천 개·BouncyCastle)가
            // 통째로 들어가기 때문인데, R8이 미사용 코드를 제거해 릴리스 용량을 크게
            // 줄인다. 반사로 로딩되는 암호 라이브러리만 proguard-rules.pro로 보존한다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true // carries the (git-ignored) TMDB key default
    }

    // Robolectric needs the merged Android resources on the unit-test classpath so
    // the screenshot harness can render real theme/components off-device.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // 컨테이너 기본 로케일(POSIX)이면 JVM이 파일명을 ASCII로 인코딩해 한글 이름이
                // 물음표가 된다. 실서버 대조 테스트는 일부러 한글 이름을 만들므로(한국어 미디어
                // 라이브러리) 포크된 테스트 JVM에 UTF-8 로케일을 준다. (core-ftp 테스트와 동일.)
                it.environment("LANG", "C.UTF-8")
                it.environment("LC_ALL", "C.UTF-8")
                it.systemProperty("file.encoding", "UTF-8")
                // WebDAV 실서버 테스트는 표준 JDK HttpURLConnection으로 비표준 메서드(MKCOL/MOVE/
                // COPY)를 보내야 한다. 그 폴백이 method 필드를 리플렉션으로 세팅하므로 java.net을 연다
                // (기기에선 OkHttp 기반이라 불필요 -- 테스트 JVM에서만 필요).
                it.jvmArgs("--add-opens", "java.base/java.net=ALL-UNNAMED")
            }
        }
    }

    // smbj's transitive jars (slf4j, Bouncy Castle) ship multi-release metadata
    // that collides on merge; drop the duplicated resources -- none is needed at
    // runtime on Android.
    packaging {
        resources {
            excludes += "/META-INF/versions/**/OSGI-INF/**"
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    // The player's kotlin lives under kotlin/, not java/, matching the reference
    // app it was ported from.
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process) // pause video when app backgrounds
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui) // PlayerView (draws the subtitle view)
    implementation(libs.media3.session) // MediaSession/MediaController/notification

    implementation(libs.commons.net) // FTP streaming + browsing
    implementation(libs.jsch) // SFTP streaming + browsing
    implementation(libs.smbj) // SMB/CIFS streaming + browsing

    implementation(libs.coil.compose) // TMDB poster/still loading + caching

    implementation(libs.jaudiotagger) // 로컬 음악 태그·앨범아트 읽기/쓰기(태그 편집)

    // The OLO Explorer protocol engine: its verified host-key (SshHostKey) and
    // TLS certificate (PinningTrustManager/ServerCertificate) primitives secure
    // the SFTP/FTPS browse and streaming paths.
    implementation(project(":core-ftp"))

    // Unit tests + the Robolectric mockup/screenshot harness (renders real OLO
    // components to PNG off-device).
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // 실제 ExoPlayer를 Robolectric에서 구동해 추출·디코드·재생 파이프라인을 검증(테스트 전용).
    testImplementation(libs.media3.test.utils)
    testImplementation(libs.media3.test.utils.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
