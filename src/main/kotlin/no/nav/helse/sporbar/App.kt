package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import com.github.navikt.tbd_libs.speed.SpeedClient
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sporbar.sis.*
import org.slf4j.LoggerFactory
import java.net.http.HttpClient

val objectMapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

fun main() {
    val log = LoggerFactory.getLogger("sporbar")
    try {
        launchApplication(System.getenv())
    } catch (e: Exception) {
        log.error("Feil under kjøring", e)
        throw e
    }
}

fun launchApplication(env: Map<String, String>) {
    val factory = ConsumerProducerFactory(AivenConfig.default)
    RapidApplication.create(env, factory).apply {
        val azureClient = createAzureTokenClientFromEnvironment(env)
        val speedClient = SpeedClient(
            httpClient = HttpClient.newHttpClient(),
            objectMapper = objectMapper,
            tokenProvider = azureClient
        )
        val spedisjonClient = SpedisjonClient(
            httpClient = HttpClient.newHttpClient(),
            objectMapper = objectMapper,
            tokenProvider = azureClient
        )

        val aivenProducer = factory.createProducer()

        val vedtakFattetMediator = VedtakFattetMediator(
            spedisjonClient = spedisjonClient,
            producer = aivenProducer
        )
        val utbetalingMediator = UtbetalingMediator(aivenProducer)

        VedtakFattetRiver(this, vedtakFattetMediator, speedClient)
        VedtaksperiodeAnnullertRiver(this, aivenProducer, speedClient)
        UtbetalingUtbetaltRiver(this, utbetalingMediator, speedClient)
        UtbetalingUtenUtbetalingRiver(this, utbetalingMediator, speedClient)
        AnnulleringRiver(this, aivenProducer, speedClient)

        val sisPublisher = KafkaSisPublisher(aivenProducer)
        BehandlingOpprettetRiver(this, spedisjonClient, sisPublisher)
        VedtaksperiodeVenterRiver(this, spedisjonClient, sisPublisher)
        BehandlingLukketRiver(this, sisPublisher)
        BehandlingForkastetRiver(this, sisPublisher)

    }.start()
}
