pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 안드로이드용 태그 쓰기 라이브러리(jaudiotagger Android 포크)는 jitpack에만 있다.
        // java.awt/ImageIO를 쓰는 상위 jthink 버전과 달리 안드로이드에서 앨범아트까지 동작한다.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "OLO Player"

// The FTP/FTPS/SFTP protocol engine, ported from OLO Explorer. Pure Kotlin/JVM,
// no Android SDK -- it builds and its tests run without one, so the proven
// transfer and host-key/certificate verification are reused rather than rewritten.
include(":core-ftp")
include(":app")
