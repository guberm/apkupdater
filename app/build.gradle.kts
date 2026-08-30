import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.rikka.tools.refine")
}

val signingProperties = Properties().apply {
    listOf(
        rootProject.file("local.properties"),
        File(System.getProperty("user.home"), ".android/apkupdater-signing.properties")
    ).filter(File::isFile).forEach { propertiesFile ->
        propertiesFile.inputStream().use(::load)
    }
}

fun signingValue(environmentName: String, propertyName: String) =
    System.getenv(environmentName)?.takeIf(String::isNotBlank)
        ?: signingProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)

val releaseStoreFile = signingValue("APKUPDATER_KEYSTORE_FILE", "keystore.file")?.let(::file)
val releaseStorePassword = signingValue("APKUPDATER_STORE_PASSWORD", "keystore.password")
val releaseKeyAlias = signingValue("APKUPDATER_KEY_ALIAS", "keystore.keyalias")
val releaseKeyPassword = signingValue("APKUPDATER_KEY_PASSWORD", "keystore.keypassword")
val releaseSigningConfigured = releaseStoreFile?.isFile == true &&
    listOf(releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }
val releaseRequested = gradle.startParameter.taskNames.any {
    val task = it.substringAfterLast(':')
    task.equals("build", ignoreCase = true) || task.contains("release", ignoreCase = true)
}
val sensitiveLogging = providers.gradleProperty("sensitiveLogging").orNull == "true"

if (releaseRequested && !releaseSigningConfigured) {
    throw GradleException("Release signing is not configured. Set APKUPDATER_* variables or ~/.android/apkupdater-signing.properties.")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.apkupdater"
    compileSdk = 36

    val buildNumber = System.getenv("BUILD_NUMBER").orEmpty()
    defaultConfig {
        applicationId = "com.apkupdater" + System.getenv("BUILD_TAG").orEmpty()
        minSdk = 23
        targetSdk = 36
        versionCode = if (buildNumber.isEmpty()) 73 else buildNumber.toInt()
        versionName = if (buildNumber.isEmpty()) "3.1.14" else "0.0.$buildNumber"
        buildConfigField("boolean", "SENSITIVE_LOGGING", sensitiveLogging.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        warning.addAll(arrayOf("ExtraTranslation", "MissingTranslation", "MissingQuantity"))
    }
}

dependencies {

    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.navigation:navigation-runtime-ktx:2.9.7")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("com.github.rumboalla.KryptoPrefs:kryptoprefs-gson:0.4.3") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
    implementation("com.github.rumboalla.KryptoPrefs:kryptoprefs:0.4.3")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.auroraoss:gplayapi:3.6.4")
    compileOnly("dev.rikka.hidden:stub:4.4.0")
    implementation("dev.rikka.tools.refine:runtime:4.4.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    implementation("io.github.g00fy2:versioncompare:1.5.0")
    implementation("io.insert-koin:koin-android:4.1.1")
    implementation("io.insert-koin:koin-androidx-compose:4.1.1")
    implementation("org.jsoup:jsoup:1.22.1")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")

}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { _ ->
            tasks.configureEach {
                if (name == "package${variant.name.replaceFirstChar { it.uppercase() }}") {
                    doLast {
                        outputs.files.files
                            .flatMap { it.walkTopDown().filter { f -> f.extension == "apk" }.toList() }
                            .forEach { apk -> runCatching { apk.copyTo(File(apk.parentFile, "${android.defaultConfig.applicationId}-${variant.buildType}.apk"), true) }.getOrNull() }
                    }
                }
            }
        }
    }
}
