plugins {
    kotlin("jvm") version "1.5.30"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

repositories {
    mavenCentral()
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}

dependencies {
    implementation("ca.uhn.hapi.fhir:org.hl7.fhir.validation:5.5.3")
    implementation("com.sksamuel.hoplite:hoplite-json:1.4.7")
    runtimeOnly("com.squareup.okhttp3:okhttp:4.9.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.0")
}
