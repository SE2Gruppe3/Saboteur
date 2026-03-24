// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("org.sonarqube") version "7.2.2.6593"
}

sonar {
    properties {
        property("sonar.organization", "SE2Gruppe3")
        property("sonar.projectKey", "SE2Gruppe3_saboteur_app")
        property("sonar.projectName", "saboteur-app")
    }
}
