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
    }
}

rootProject.name = "Dukaan AI"
include(":app")
include(":core:core-ui")
include(":core:core-db")
include(":core:core-network")
include(":core:core-voice")
include(":feature-billing")
include(":feature-khata")
include(":feature-dashboard")
include(":feature-ocr")
include(":feature-orders")
