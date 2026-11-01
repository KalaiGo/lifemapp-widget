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

rootProject.name = "lifeMappWidgets"

include(
    ":app",
    ":core:model",
    ":core:crypto",
    ":core:data",
    ":core:designsystem",
    ":core:weblauncher",
    ":widget",
)
