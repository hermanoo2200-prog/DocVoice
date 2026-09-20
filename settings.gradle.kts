pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (passo 6, OCR) não está no Maven Central: o próprio
        // projecto distribui pelo JitPack. Só lhe é dado esse grupo — tudo o
        // resto continua a vir de onde vinha, para o JitPack não passar a ser
        // porta de entrada de dependências que ninguém escolheu.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("cz.adaptech.tesseract4android") }
        }
    }
}

rootProject.name = "DocVoice"
include(":app")
