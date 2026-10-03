// The engine is packaged as its own (never installed) APK. The app loads its dex
// code at runtime, which lets the YouTube part be replaced without reinstalling.
plugins {
    id("com.android.application")
}

// Release signing comes from repository secrets in CI; without them (local builds)
// the debug key is used so everything still installs.
val ksPath: String? = System.getenv("SIGNING_KEYSTORE")
val hasReleaseKey = !ksPath.isNullOrBlank() && file(ksPath).exists()

val extractorVersion = file("extractor.version").readText().trim()
val engineVersion = (System.getenv("ENGINE_VERSION") ?: "1").toInt()

android {
    namespace = "vn.lucbao.engine"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.lucbao.engine"
        minSdk = 26
        targetSdk = 35
        versionCode = engineVersion
        versionName = extractorVersion
        buildConfigField("String", "EXTRACTOR_VERSION", "\"$extractorVersion\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("lucbao") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = System.getenv("SIGNING_ALIAS") ?: "lucbao"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("lucbao") ?: signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    compileOnly(project(":engine-api"))
    implementation("com.github.TeamNewPipe:NewPipeExtractor:$extractorVersion")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
