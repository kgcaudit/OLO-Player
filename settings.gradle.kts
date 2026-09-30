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
    }
}

rootProject.name = "OLO Player"

// The FTP/FTPS/SFTP protocol engine, ported from OLO Explorer. Pure Kotlin/JVM,
// no Android SDK -- it builds and its tests run without one, so the proven
// transfer and host-key/certificate verification are reused rather than rewritten.
include(":core-ftp")
include(":app")
