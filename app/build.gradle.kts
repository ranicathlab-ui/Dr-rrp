plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.postpci.drrrp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.postpci.drrrp"
        // Min SDK 26 (not the latest) — many patients are older and less tech-savvy;
        // don't assume newest devices.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        // Must match compileOptions above, or compileDebugKotlin fails with
        // "Inconsistent JVM-target compatibility" when Gradle runs on a newer JDK
        // (e.g. Android Studio's bundled JBR) than the app's Java target.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    // Base Material3 XML theme (Theme.Material3.Dark.NoActionBar) used before Compose content
    // attaches; the actual UI theming is all Compose (ui/theme/Theme.kt).
    implementation(libs.material)

    // Local storage: Room, encrypted at rest via SQLCipher's SupportSQLiteOpenHelper.Factory.
    // Room 2.7.2+ required — 2.6.1 hits a KSP2 bug ("unexpected jvm signature V") on suspend
    // DAO functions.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)

    // REST sync layer + offline queue (Stage 7).
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase Auth (email/password) + Firestore (role field) — see AuthGateway/FirebaseAuthGateway.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    // FirebaseUser.getIdToken(...) and Firestore calls return play-services Task, not a
    // coroutine; this adds the Task<T>.await() extension FirebaseAuthGateway relies on.
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
