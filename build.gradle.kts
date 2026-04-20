plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    kotlin("multiplatform") version "2.1.20" apply false
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.spring") version "2.1.20" apply false
    id("org.springframework.boot") version "3.4.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.sonarqube") version "7.2.2.6593"
}

sonar {
    properties {
        property("sonar.organization", "se2gruppe3")
        property("sonar.projectKey", "SE2Gruppe3_saboteur_app")
        property("sonar.projectName", "saboteur-app")
        property("sonar.sources", "app/app/src/main/java/com/aau/saboteur/viewModels")
        property("sonar.tests", "app/app/src/test/java")
        property("sonar.java.binaries", "app/app/build/tmp/kotlin-classes/debug,app/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
        property("sonar.junit.reportPaths", "app/app/build/test-results/testDebugUnitTest")
        property("sonar.coverage.jacoco.xmlReportPaths", "app/app/build/reports/jacoco/jacocoTestDebugUnitTestReport/jacocoTestDebugUnitTestReport.xml")
    }
}
