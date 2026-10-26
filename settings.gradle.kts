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

rootProject.name = "re-schools"
include(":core")
// Без Android SDK (например, на машине разработчика без SDK) ядро собирается и тестируется отдельно:
// gradle -PcoreOnly :core:test
if (!providers.gradleProperty("coreOnly").isPresent) include(":app")
