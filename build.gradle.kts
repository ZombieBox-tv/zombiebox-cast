plugins {
    id("com.android.library") version "8.13.2" apply false
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.3.0"
}

android {
    namespace = "io.github.diegog0477.zombiebox.cast"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.github.diegog0477.zombiebox.cast"
        minSdk = 21
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.0-dev.10"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint { abortOnError = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }

dependencies {
    implementation(project(":shared"))
    testImplementation("junit:junit:4.13.2")
}
