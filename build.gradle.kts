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

    shadowJar {
        minimize {
            exclude(dependency("com.sksamuel.hoplite:.*"))
            exclude(dependency("ca.uhn.hapi.fhir:hapi-fhir-structures-r5:.*"))
        }
    }
}

dependencies {
    implementation("org.junit.platform:junit-platform-engine:1.8.0")
    implementation("ca.uhn.hapi.fhir:org.hl7.fhir.validation:5.5.3")
    implementation("com.sksamuel.hoplite:hoplite-core:1.4.7")
    runtimeOnly("ca.uhn.hapi.fhir:hapi-fhir-structures-r5:5.5.1")
    runtimeOnly("com.sksamuel.hoplite:hoplite-json:1.4.7")
    runtimeOnly("com.sksamuel.hoplite:hoplite-yaml:1.4.7")
    runtimeOnly("com.squareup.okhttp3:okhttp:4.9.1")
    runtimeOnly("org.slf4j:slf4j-nop:1.7.32")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.0")
    testImplementation("org.junit.platform:junit-platform-testkit:1.8.0")
}
