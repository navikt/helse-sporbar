plugins {
    alias(libs.plugins.sas.deployable)
}

sasDeployable {
    mainClass = "no.nav.helse.sporbar.AppKt"
}

dependencies {
    implementation(libs.rapidsAndRivers)
    implementation(libs.tbdLibs.azureTokenClientDefault)
    implementation(libs.tbdLibs.retry)
    implementation(libs.tbdLibs.speedClient)
    implementation(libs.tbdLibs.spedisjonClient)

    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.auth.jwt) {
        exclude(group = "junit")
    }

    testImplementation(libs.tbdLibs.rapidsAndRiversTest)
    testImplementation(libs.mockk)
    testImplementation(libs.jsonSchemaValidator)
    testImplementation(libs.jsonassert)
}
