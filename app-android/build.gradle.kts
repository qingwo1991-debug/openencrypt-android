import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runtimeAbis = listOf("arm64-v8a", "armeabi-v7a")
val runtimeNames = listOf("openlist-runtime", "openencrypt-gateway")
val runtimeAssetRoot = file("src/main/assets/bin")

fun pickFirstExisting(candidates: List<File>): File? = candidates.firstOrNull { it.isFile }

val syncAndroidRuntimeBinaries by tasks.registering {
    group = "build setup"
    description = "Sync Android runtime binaries into app assets."
    doLast {
        delete(runtimeAssetRoot)
        val customRoot = providers.environmentVariable("RUNTIME_BIN_ROOT").orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
        val repoRoot = rootDir

        runtimeAbis.forEach { abi ->
            val outDir = File(runtimeAssetRoot, abi).apply { mkdirs() }
            val openListSrc = pickFirstExisting(
                listOfNotNull(
                    customRoot?.let { File(it, "$abi/openlist-runtime") },
                    File(repoRoot, "core-openlist-go/target/android/$abi/openlist-runtime")
                )
            )
            val gatewaySrc = pickFirstExisting(
                listOfNotNull(
                    customRoot?.let { File(it, "$abi/openencrypt-gateway") },
                    File(repoRoot, "core-encrypt-rs/target/android/$abi/openencrypt-gateway")
                )
            )
            if (openListSrc != null) {
                copy {
                    from(openListSrc)
                    into(outDir)
                }
            }
            if (gatewaySrc != null) {
                copy {
                    from(gatewaySrc)
                    into(outDir)
                }
            }
        }
    }
}

val verifyAndroidRuntimeBinaries by tasks.registering {
    group = "verification"
    description = "Fail build when required Android runtime binaries are missing from assets."
    dependsOn(syncAndroidRuntimeBinaries)
    doLast {
        val missing = mutableListOf<String>()
        runtimeAbis.forEach { abi ->
            runtimeNames.forEach { name ->
                val file = File(runtimeAssetRoot, "$abi/$name")
                if (!file.isFile) {
                    missing += "src/main/assets/bin/$abi/$name"
                }
            }
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Missing Android runtime binaries:")
                    missing.forEach { appendLine("- $it") }
                    appendLine("Provide binaries via one of:")
                    appendLine("1) RUNTIME_BIN_ROOT=<dir> with <dir>/<abi>/<binary>")
                    appendLine("2) core-openlist-go/target/android/<abi>/openlist-runtime")
                    appendLine("3) core-encrypt-rs/target/android/<abi>/openencrypt-gateway")
                }
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyAndroidRuntimeBinaries)
}

android {
    namespace = "org.openlist.encrypt.android"
    compileSdk = 35

    val buildVersionName = System.getenv("BUILD_VERSION_NAME") ?: "0.1.0"
    val buildVersionCodeRaw = System.getenv("BUILD_VERSION_CODE") ?: "1"
    val buildVersionCode = buildVersionCodeRaw.toIntOrNull()
        ?: error("BUILD_VERSION_CODE must be an integer, got: $buildVersionCodeRaw")
    require(buildVersionCode > 0) {
        "BUILD_VERSION_CODE must be > 0, got: $buildVersionCode"
    }
    val hasReleaseSigningEnv = listOf(
        "ANDROID_KEYSTORE_PATH",
        "ANDROID_KEYSTORE_PASSWORD",
        "ANDROID_KEY_ALIAS",
        "ANDROID_KEY_PASSWORD"
    ).all { !System.getenv(it).isNullOrBlank() }

    defaultConfig {
        applicationId = "org.openlist.encrypt.android"
        minSdk = 21
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasReleaseSigningEnv) {
        signingConfigs {
            create("release") {
                val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH").orEmpty()
                val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD").orEmpty()
                val keyAlias = System.getenv("ANDROID_KEY_ALIAS").orEmpty()
                val keyPassword = System.getenv("ANDROID_KEY_PASSWORD").orEmpty()
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigningEnv) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
