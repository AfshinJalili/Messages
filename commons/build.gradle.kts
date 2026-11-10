import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Shared UI and helpers, vendored from org.fossify:commons 6.1.6 (GPL-3.0) with the
// upstream package-name, signature, sideload and donation checks removed.
plugins {
    alias(libs.plugins.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.parcelize)
}

android {
    namespace = "org.fossify.commons"

    compileSdk = libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = libs.versions.app.build.minimumSDK.get().toInt()
        vectorDrawables.useSupportLibrary = true
        consumerProguardFiles("consumer-rules.pro")
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    compileOptions {
        val currentJavaVersionFromLibs =
            JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
        compilerOptions.freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi",
        )
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlin.immutable.collections)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.ez.vcard)

    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.commons.compose)
    implementation(libs.commons.compose.view.binding)
    debugImplementation(libs.bundles.commons.compose.preview)

    api(libs.joda.time)
    api(libs.recyclerView.fastScroller)
    api(libs.reprint)
    api(libs.rtl.viewpager)
    api(libs.patternLockView)
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    api(libs.material)
    api(libs.gson)

    implementation(libs.glide.compose)
    api(libs.glide)
    ksp(libs.glide.compiler)

    api(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
}
