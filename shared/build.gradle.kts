plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")

}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.add("-java-parameters")
        }
    }
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
            }
        }
    }

}
