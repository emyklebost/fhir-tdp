import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.30"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.0"
    `java-library`
}

group = "no.nav.helse"
version = "0.1.0"

repositories {
    mavenCentral()
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    shadowJar {
        dependencies {
            // These dependencies are already available in the junit-platform-console-standalone.jar
            // and validator_cli.jar, can therefore be omitted to reduce size.
            exclude(dependency("com.fasterxml.*::"))
            exclude(dependency("org.junit.*::"))
        }
    }

    jar {
        manifest {
            attributes(
                mapOf(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version
                )
            )
        }
    }
}

dependencies {
    compileOnly("ca.uhn.hapi.fhir:org.hl7.fhir.validation:5.5.3")
    implementation("com.sksamuel.hoplite:hoplite-json:1.4.7")
    implementation("com.sksamuel.hoplite:hoplite-yaml:1.4.7")
    implementation("org.junit.platform:junit-platform-engine:1.8.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.0")
    testImplementation("org.junit.platform:junit-platform-testkit:1.8.0")
    testRuntimeOnly("ca.uhn.hapi.fhir:org.hl7.fhir.validation:5.5.3")
    testRuntimeOnly("com.squareup.okhttp3:okhttp:4.9.1")
    testRuntimeOnly("org.slf4j:slf4j-nop:1.7.32")
}
