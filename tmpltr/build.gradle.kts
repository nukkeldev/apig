import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.22"
    application
}

group = "io.github.nukkeldev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val log4jVersion: String by project

dependencies {
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

application {
    mainClass.set("$group.MainKt")
}