import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.begood.bmp"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.begood.bmp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Assinatura oficial opcional. A chave NÃO fica no repositório:
    // coloque o arquivo em app/assinatura/bmp.jks e a senha em keystore.properties (senha=...),
    // ambos ignorados pelo git. Sem a chave, o APK sai com a chave de debug padrão.
    val chave = file("assinatura/bmp.jks")
    val local = Properties().apply {
        val arq = rootProject.file("keystore.properties")
        if (arq.exists()) arq.inputStream().use { load(it) }
    }
    fun segredo(env: String, prop: String): String? =
        System.getenv(env)?.takeIf { it.isNotBlank() } ?: local.getProperty(prop)?.takeIf { it.isNotBlank() }

    val senhaChave = segredo("KEYSTORE_PASSWORD", "senha")
    val temAssinatura = chave.exists() && senhaChave != null

    signingConfigs {
        if (temAssinatura) {
            create("oficial") {
                storeFile = chave
                storePassword = senhaChave
                keyAlias = segredo("KEY_ALIAS", "alias") ?: "androiddebugkey"
                keyPassword = segredo("KEY_PASSWORD", "senhaAlias") ?: senhaChave
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (temAssinatura) signingConfigs.getByName("oficial") else signingConfigs.getByName("debug")
        }
        debug {
            if (temAssinatura) signingConfig = signingConfigs.getByName("oficial")
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
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Reprodutor (segundo plano, notificação, tela de bloqueio, Bluetooth/AVRCP, carro)
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")

    // Busca online (letras, dados do álbum e capas) e imagens
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Leitura e gravação de tags (MP3, FLAC, M4A, OGG...)
    implementation("net.jthink:jaudiotagger:3.0.1")
}
