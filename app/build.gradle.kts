plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.olo.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.olo.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}
