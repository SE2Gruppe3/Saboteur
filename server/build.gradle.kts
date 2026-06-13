import java.time.Instant
import java.time.format.DateTimeFormatter

plugins {
	application
	jacoco
	kotlin("jvm")
	kotlin("plugin.spring")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
	id("org.sonarqube") version "7.2.2.6593"
}

group = "com.aau"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

application {
	mainClass.set("com.aau.server.ServerApplicationKt")
}

springBoot {
	mainClass.set("com.aau.server.ServerApplicationKt")
	buildInfo()
}

dependencies {
	implementation(project(":shared"))
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-mustache")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.h2database:h2")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("com.h2database:h2")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xjsr305=strict",
			"-java-parameters"
		)
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.named("sonar") {
	dependsOn(tasks.jacocoTestReport)
}

sonar {
	properties {
		property("sonar.organization", "se2gruppe3")
		property("sonar.projectKey", "SE2Gruppe3_saboteur_server")
		property("sonar.projectName", "saboteur-server")
		property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
	}
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	archiveFileName.set("server.jar")
}

// Custom Task: Generiert die git.properties - kompatibel mit Local & GitHub Actions
val generateGitProperties by tasks.registering {
	val outputFile = layout.buildDirectory.file("resources/main/git.properties")
	outputs.file(outputFile)

	doLast {
		// 1. Commit SHA ermitteln (GitHub Actions Variable ODER lokal via Git)
		val commitId = System.getenv("GITHUB_SHA") ?: try {
			project.providers.exec {
				commandLine("git", "rev-parse", "HEAD")
			}.standardOutput.asText.get().trim()
		} catch (e: Exception) {
			"unknown"
		}

		val shortCommitId = if (commitId != "unknown") commitId.take(7) else "unknown"

		// 2. Branch-Namen ermitteln (GitHub Actions Variable ODER lokal via Git)
		val branch = System.getenv("GITHUB_REF_NAME") ?: try {
			project.providers.exec {
				commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
			}.standardOutput.asText.get().trim()
		} catch (e: Exception) {
			"main"
		}

		val file = outputFile.get().asFile
		file.parentFile.mkdirs()
		file.writeText("""
            git.branch=$branch
            git.commit.id.full=$commitId
            git.commit.id.abbrev=$shortCommitId
            git.commit.time=${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}
        """.trimIndent())
	}
}

// Verknüpfung mit dem Ressourcen-Prozess
tasks.processResources {
	dependsOn(generateGitProperties)
}