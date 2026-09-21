plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pt.docvoice"
    compileSdk = 35

    defaultConfig {
        applicationId = "pt.docvoice"
        minSdk = 26          // Android 8.0
        targetSdk = 35
        versionCode = 15
        versionName = "0.7.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    splits {
        // Um ficheiro por arquitectura, em vez de um com as quatro dentro.
        // NÃO é o mesmo que cortar arquitecturas: continuam a sair todas,
        // incluindo x86_64, que é a do emulador onde isto se prova. O que
        // muda é que cada telemóvel leva só a sua — de 52 MB para cerca de
        // 30 — e o universal continua a sair para quem não quiser escolher.
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    androidResources {
        // Os ficheiros de idioma do Tesseract ficam por comprimir de propósito.
        // Comprimidos, o AssetManager.openFd() recusa-se a abri-los — é assim
        // que está feito — e ficávamos sem saber o tamanho para comparar com o
        // que já está copiado. Por cima disso, ficar por comprimir poupa a
        // descompressão em cada arranque, que num telemóvel antigo conta.
        noCompress += "traineddata"
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Lista de recentes e posição guardada. Sem base de dados: são três campos.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Extracção de texto. O PdfRenderer do sistema (raster para o OCR) entra no passo 6.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Passo 6, OCR. Motor dentro do telemóvel, ficheiros de idioma em assets:
    // a aplicação não tem autorização de rede e não vai buscar nada a lado nenhum.
    // Coordenadas lidas no README do próprio projecto (adaptech-cz/Tesseract4Android).
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    testImplementation("junit:junit:4.13.2")

    // Passo 6: o OCR só se prova com o motor a trabalhar a sério, e o motor é
    // nativo. Estes testes correm no emulador, não na JVM.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
