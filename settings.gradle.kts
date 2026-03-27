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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("app/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "SaboteurGame"

include(":app")
project(":app").projectDir = file("app/app")

include(":server")
project(":server").projectDir = file("server")

include(":shared")
project(":shared").projectDir = file("shared")
