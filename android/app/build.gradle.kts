import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// SUPABASE_URL y SUPABASE_ANON_KEY salen de android/local.properties (no se sube a git)
// o de variables de entorno (CI).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun config(name: String): String = localProps.getProperty(name) ?: System.getenv(name) ?: ""

// Una sola versión para todo el proyecto: la de desktop/src-tauri/tauri.conf.json
// (la sube scripts/release.sh). versionCode = major·10000 + minor·100 + patch.
val appVersion: String = Regex("\"version\"\\s*:\\s*\"([0-9.]+)\"")
    .find(rootProject.file("../desktop/src-tauri/tauri.conf.json").readText())
    ?.groupValues?.get(1) ?: "0.0.0"
val appVersionCode: Int = appVersion.split('.').map { it.toIntOrNull() ?: 0 }
    .let { (it.getOrElse(0) { 0 } * 10000) + (it.getOrElse(1) { 0 } * 100) + it.getOrElse(2) { 0 } }

android {
    namespace = "app.fouru"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.fouru"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion

        buildConfigField("String", "SUPABASE_URL", "\"${config("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${config("SUPABASE_ANON_KEY")}\"")
    }

    // Keystore de release fijo (DECISIONS §14). En local: RELEASE_* en local.properties;
    // en el CI: variables de entorno con el mismo nombre (secretos de GitHub).
    signingConfigs {
        create("release") {
            val store = config("RELEASE_STORE_FILE")
            if (store.isNotEmpty()) {
                storeFile = file(store)
                storePassword = config("RELEASE_STORE_PASSWORD")
                keyAlias = config("RELEASE_KEY_ALIAS")
                keyPassword = config("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)

    // Vídeo: comprimir a 720p antes de enviar y reproducir a pantalla completa.
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // QR del código de invitación: escanear (Google, sin permiso de cámara) y mostrar.
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.core)
}
