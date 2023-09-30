import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

val mainClass = "no.nav.helse.sporbar.AppKt"
val jvmTarget = "17"

val rapidsAndRiversVersion = "2023093008351696055717.ffdec6aede3d"
val ktorVersion = "2.3.4"
val junitJupiterVersion = "5.10.0"
val testcontainersVersion = "1.19.0"
val mockkVersion = "1.13.2"
val postgresqlVersion = "42.6.0"
val kotliqueryVersion = "1.9.0"
val hikariCPVersion = "5.0.1"
val flywaycoreVersion = "9.15.0"
val jsonSchemaValidatorVersion = "1.0.73"
val jsonassertVersion = "1.5.1"

plugins {
    kotlin("jvm") version "1.9.10"
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }

    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywaycoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testImplementation("org.skyscreamer:jsonassert:$jsonassertVersion")

}

repositories {
    maven("https://jitpack.io")
    mavenCentral()
}

tasks {

    named<KotlinCompile>("compileKotlin") {
        kotlinOptions.jvmTarget = jvmTarget
    }

    named<KotlinCompile>("compileTestKotlin") {
        kotlinOptions.jvmTarget = jvmTarget
    }

    withType<Wrapper> {
        gradleVersion = "8.3"
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
            exceptionFormat = FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }

    withType<Jar> {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = mainClass
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                if (!file.exists()) it.copyTo(file)
            }
        }
    }
}
