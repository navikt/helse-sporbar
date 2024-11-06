package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.speed.SpeedClient
import java.net.http.HttpClient
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sporbar.sis.BehandlingForkastetRiver
import no.nav.helse.sporbar.sis.BehandlingLukketRiver
import no.nav.helse.sporbar.sis.BehandlingOpprettetRiver
import no.nav.helse.sporbar.sis.KafkaSisPublisher
import no.nav.helse.sporbar.sis.VedtaksperiodeVenterRiver
import org.slf4j.LoggerFactory

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
        val dataSourceBuilder = DataSourceBuilder()
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                dataSourceBuilder.migrate()
            }
        })

        val azureClient = createAzureTokenClientFromEnvironment(env)
        val speedClient = SpeedClient(
            httpClient = HttpClient.newHttpClient(),
            objectMapper = objectMapper,
            tokenProvider = azureClient
        )

        val dokumentDao = DokumentDao(dataSourceBuilder::dataSource)
        val aivenProducer = factory.createProducer()

        val vedtakFattetMediator = VedtakFattetMediator(
            dokumentDao = dokumentDao,
            producer = aivenProducer
        )
        val utbetalingMediator = UtbetalingMediator(aivenProducer)

        NyttDokumentRiver(this, dokumentDao)
        VedtakFattetRiver(this, vedtakFattetMediator, speedClient)
        VedtaksperiodeAnnullertRiver(this, aivenProducer, speedClient)
        UtbetalingUtbetaltRiver(this, utbetalingMediator, speedClient)
        UtbetalingUtenUtbetalingRiver(this, utbetalingMediator, speedClient)
        AnnulleringRiver(this, aivenProducer, speedClient)

        val sisPublisher = KafkaSisPublisher(aivenProducer)
        BehandlingOpprettetRiver(this, dokumentDao, sisPublisher)
        VedtaksperiodeVenterRiver(this, dokumentDao, sisPublisher)
        BehandlingLukketRiver(this, sisPublisher)
        BehandlingForkastetRiver(this, sisPublisher)

    }.start()
}
