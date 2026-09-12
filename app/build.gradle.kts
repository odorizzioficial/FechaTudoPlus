plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Versão em um lugar só: usada no app e no nome do arquivo gerado.
val versaoApp = "1.4.8"
val versaoCodigo = 13

android {
    namespace = "com.bgcontrol.plus"
    compileSdk = 35

    defaultConfig {
        // Identificador do app na loja e no sistema. Diferente do namespace do
        // código (com.bgcontrol.plus), que só organiza os pacotes Kotlin e não
        // precisa mudar — trocá-lo renomearia dezenas de arquivos sem ganho.
        applicationId = "com.odorizzi.fechatudoplus"
        minSdk = 26
        targetSdk = 35
        // O launcher guarda o ícone num cache indexado por pacote e versão.
        // Reinstalar com o mesmo versionCode faz vários launchers continuarem
        // servindo o ícone antigo mesmo com o APK novo. Subir a versão força a
        // reindexação.
        versionCode = versaoCodigo
        versionName = versaoApp
        resourceConfigurations += listOf(
            "pt-rBR", "pt-rPT", "en", "es", "ro", "fr", "zh-rCN", "ja", "ko", "hi"
        )
    }

    /**
     * Assinatura lida de variáveis de ambiente. Assim a chave nunca fica no
     * código nem no repositório: no GitHub Actions ela vem dos secrets, e na
     * sua máquina o Android Studio continua usando o diálogo de sempre.
     */
    signingConfigs {
        create("release") {
            val caminhoChave = System.getenv("KEYSTORE_FILE")
            if (caminhoChave != null) {
                storeFile = file(caminhoChave)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            val assinaturaRelease = signingConfigs.getByName("release")
            if (assinaturaRelease.storeFile != null) {
                signingConfig = assinaturaRelease
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

/**
 * Nome do APK gerado. Sem isto o Gradle entrega "app-debug.apk" e
 * "app-release.apk", que não dizem nada sobre o aplicativo nem sobre a versão.
 */
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val arquivo = output as? com.android.build.api.variant.impl.VariantOutputImpl
            arquivo?.outputFileName?.set("FechaTudoPlus-$versaoApp-${variant.name}.apk")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room + SQLite (banco 100% local)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (configurações)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Liquid Glass — refração SDF real no sistema de Views (API 24+)
    implementation("com.github.QWEA0:liquidglass:v2.0.5")

    // Shizuku
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
