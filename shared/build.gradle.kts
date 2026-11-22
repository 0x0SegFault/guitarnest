import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// Music logic (pitch, tunings, tabs) shared by the Android app and a future iOS app.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // Plain JVM target: runs the tests fast on any machine.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // iOS targets need macOS and Xcode. Declaring them only there keeps Linux and F-Droid
    // builds free of the Kotlin/Native toolchain download.
    if (System.getProperty("os.name").startsWith("Mac")) {
        // ./gradlew :shared:assembleGuitarNestSharedReleaseXCFramework -> shared/build/XCFrameworks/release/
        val xcframework = XCFramework("GuitarNestShared")
        listOf(iosArm64(), iosSimulatorArm64()).forEach {
            it.binaries.framework {
                baseName = "GuitarNestShared"
                isStatic = true
                xcframework.add(this)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "app.guitarnest.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
