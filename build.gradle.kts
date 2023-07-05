
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
    application
}

group = "io.github.nukkeldev"
version = "1.0-SNAPSHOT"

val ktorVersion: String by project
val log4jVersion: String by project

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")

    implementation(project(":tmpltr"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

application {
    mainClass.set("$group.MainKt")
}