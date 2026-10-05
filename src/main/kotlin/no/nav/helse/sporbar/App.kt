package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import com.github.navikt.tbd_libs.speed.SpeedClient
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sporbar.sis.*
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.net.http.HttpClient

val objectMapper: ObjectMapper =
    jacksonMapperBuilder()
        .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
        .build()

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
    RapidApplication
        .create(env, factory)
        .apply {
            val azureClient = createAzureTokenClientFromEnvironment(env)
            val speedClient =
                SpeedClient(
                    httpClient = HttpClient.newHttpClient(),
                    objectMapper = objectMapper,
                    tokenProvider = azureClient,
                )
            val spedisjonClient =
                SpedisjonClient(
                    httpClient = HttpClient.newHttpClient(),
                    objectMapper = objectMapper,
                    tokenProvider = azureClient,
                )

            val spForsikringClient =
                SpForsikringClient(
                    httpClient = HttpClient.newHttpClient(),
                    objectMapper = objectMapper,
                    tokenProvider = azureClient,
                    baseUrl = env.getValue("SP_FORSIKRING_BASE_URL"),
                    scope = env.getValue("SP_FORSIKRING_SCOPE"),
                )

            val aivenProducer = factory.createProducer()

            val vedtakFattetMediator =
                VedtakFattetMediator(
                    spedisjonClient = spedisjonClient,
                    producer = aivenProducer,
                    spForsikringClient = spForsikringClient,
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
