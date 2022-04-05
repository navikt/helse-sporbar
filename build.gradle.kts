import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion = "1.6.7"
val junitJupiterVersion = "5.8.2"
val mainClass = "no.nav.helse.sporbar.AppKt"
val testcontainersVersion = "1.16.2"

plugins {
    kotlin("jvm") version "1.6.0"
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2022.04.05-09.40.11a466d7ac70")

    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }

    implementation("com.zaxxer:HikariCP:4.0.2")
    implementation("no.nav:vault-jdbc:1.3.7")
    implementation("org.flywaydb:flyway-core:7.5.4")
    implementation("com.github.seratch:kotliquery:1.3.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("io.mockk:mockk:1.12.0")
}

repositories {
    maven("https://jitpack.io")
    mavenCentral()
}

tasks {

    named<KotlinCompile>("compileKotlin") {
        kotlinOptions.jvmTarget = "17"
    }

    named<KotlinCompile>("compileTestKotlin") {
        kotlinOptions.jvmTarget = "17"
    }

    withType<Wrapper> {
        gradleVersion = "7.4"
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
