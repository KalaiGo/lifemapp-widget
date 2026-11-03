// The presenter: cache → WidgetUi, shared by the Glance widget and the app's Today screen. Plain Kotlin, no Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:crypto"))

    testImplementation(libs.junit4)
}
