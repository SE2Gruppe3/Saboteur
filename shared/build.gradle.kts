plugins {
    kotlin("multiplatform")

}

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test")) // Diese Zeile ist entscheidend!
            }
        }
    }

}
