val mainClass = "no.nav.helse.sporbar.AppKt"

val rapidsAndRiversVersion = "2025061811051750237542.df739400e55e"
val tbdLibsVersion = "2025.03.30-14.11-a91ce546"
val ktorVersion = "3.1.2"
val junitJupiterVersion = "5.12.1"
val mockkVersion = "1.13.17"
val jsonSchemaValidatorVersion = "1.0.73"
val jsonassertVersion = "1.5.1"

plugins {
    kotlin("jvm") version "2.1.20"
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    implementation("com.github.navikt.tbd-libs:azure-token-client-default:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:retry:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:speed-client:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:spedisjon-client:$tbdLibsVersion")

    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }

    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testImplementation("org.skyscreamer:jsonassert:$jsonassertVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

repositories {
    val githubPassword: String? by project
    mavenCentral()
    /* ihht. https://github.com/navikt/utvikling/blob/main/docs/teknisk/Konsumere%20biblioteker%20fra%20Github%20Package%20Registry.md
        så plasseres github-maven-repo (med autentisering) før nav-mirror slik at github actions kan anvende førstnevnte.
        Det er fordi nav-mirroret kjører i Google Cloud og da ville man ellers fått unødvendige utgifter til datatrafikk mellom Google Cloud og GitHub
     */
    maven {
        url = uri("https://maven.pkg.github.com/navikt/maven-release")
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
    }
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("21"))
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
        }

        val parallellDisabled = System.getenv("CI" ) == "true"
        systemProperty("junit.jupiter.execution.parallel.enabled", parallellDisabled.not().toString())
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
        systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
        systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "8")
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
