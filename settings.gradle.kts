pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Liquid Glass (AAR com .so pré-compilado, sem necessidade de NDK)
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Fecha Tudo Plus"
include(":app")
