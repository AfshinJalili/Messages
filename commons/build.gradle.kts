import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Vendored shared UI and helpers (GPL-3.0). The package-name, signature,
// sideload and donation checks stay removed.
plugins {
    alias(libs.plugins.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.parcelize)
}

android {
    namespace = "com.afsh.commons"

    compileSdk = libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = libs.versions.app.build.minimumSDK.get().toInt()
        vectorDrawables.useSupportLibrary = true
        consumerProguardFiles("consumer-rules.pro")
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
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
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.ez.vcard)

    implementation(libs.bundles.lifecycle)

    api(libs.joda.time)
    api(libs.reprint)
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    api(libs.material)
    api(libs.gson)

    api(libs.glide)
    ksp(libs.glide.compiler)

    api(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
}