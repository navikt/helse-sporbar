import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val mainClass = "no.nav.helse.sporbar.AppKt"
val jvmTarget = "17"

val rapidsAndRiversVersion = "2022.04.21-09.34.08966130226f"
val ktorVersion = "2.0.0"
val junitJupiterVersion = "5.8.2"
val testcontainersVersion = "1.17.1"
val mockkVersion = "1.12.3"
val kotliqueryVersion = "1.3.1"
val hikariCPVersion = "4.0.2"
val vaultjdbcVersion = "1.3.7"
val flywaycoreVersion = "7.5.4"
val jsonSchemaValidatorVersion = "1.0.70"
val jsonassertVersion = "1.5.0"

plugins {
    kotlin("jvm") version "1.6.21"
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
    implementation("no.nav:vault-jdbc:$vaultjdbcVersion")
    implementation("org.flywaydb:flyway-core:$flywaycoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
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
        gradleVersion = "7.4.2"
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
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
                val file = File("$buildDir/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }
}
