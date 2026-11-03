import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun config(key: String, env: String, default: String = ""): String =
    localProps.getProperty(key) ?: providers.gradleProperty(key).orNull ?: System.getenv(env) ?: default

android {
    namespace = "com.kalai73.lifemapp.widgets"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.kalai73.lifemapp.widgets"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["authRedirectScheme"] = "lifemappwidgets"
    }

    signingConfigs {
        create("release") {
            val keystorePath = config(
                "lifemapp.keystoreFile",
                "LIFEMAPP_KEYSTORE_FILE",
                "debug.keystore"
            )
            val keystoreFile = file(keystorePath).let { if (it.isAbsolute) it else rootProject.file(keystorePath) }
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = config("lifemapp.keystorePassword", "LIFEMAPP_KEYSTORE_PASSWORD", "android")
                keyAlias = config("lifemapp.keyAlias", "LIFEMAPP_KEY_ALIAS", "androiddebugkey")
                keyPassword = config("lifemapp.keyPassword", "LIFEMAPP_KEY_PASSWORD", "android")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:presentation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:weblauncher"))
    implementation(project(":widget"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.glance.appwidget)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit4)
}
