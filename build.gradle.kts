plugins {
    kotlin("jvm") version "1.5.30"
    id("com.github.johnrengelman.shadow") version "7.0.0"
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
            attributes(mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version)
            )
        }
    }
}

dependencies {
    api("org.junit.platform:junit-platform-engine:1.8.0")
    implementation("ca.uhn.hapi.fhir:org.hl7.fhir.validation:5.5.3")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r5:5.5.1")
    implementation("com.sksamuel.hoplite:hoplite-json:1.4.7")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("org.slf4j:slf4j-nop:1.7.32")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.0")
}
