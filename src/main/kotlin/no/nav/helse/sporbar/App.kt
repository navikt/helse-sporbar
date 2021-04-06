package no.nav.helse.sporbar

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.RapidApplication
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

fun main() {
    val log = LoggerFactory.getLogger("sporbar")
    val env = Environment(System.getenv())
    try {
        launchApplication(env)
    } catch (e: Exception) {
        log.error("Feil under kjøring", e)
        throw e
    }
}

fun launchApplication(env: Environment) {
    val dataSource = DataSourceBuilder(env.db)
        .apply(DataSourceBuilder::migrate)
        .getDataSource()

    val dokumentDao = DokumentDao(dataSource)
    val producer =
        KafkaProducer<String, JsonNode>(
            loadBaseConfig(
                env.raw.getValue("KAFKA_BOOTSTRAP_SERVERS"),
                env.serviceUser
            ).toProducerConfig()
        )
    val vedtaksperiodeDao = VedtaksperiodeDao(dataSource)
    val vedtakDao = VedtakDao(dataSource)
    val mediator = VedtaksperiodeMediator(
        vedtaksperiodeDao = vedtaksperiodeDao,
        vedtakDao = vedtakDao,
        dokumentDao = dokumentDao,
        producer = producer
    )
    val vedtakFattetMediator = VedtakFattetMediator(
        dokumentDao = dokumentDao,
        producer = producer
    )
    val utbetalingMediator = UtbetalingMediator(
        producer = producer
    )

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .build().apply {
            NyttDokumentRiver(this, dokumentDao)
            VedtaksperiodeEndretRiver(this, mediator)
            UtbetaltRiver(this, mediator)
            VedtakFattetRiver(this, vedtakFattetMediator)
            UtbetalingUtbetaltRiver(this, utbetalingMediator)
            UtbetalingUtenUtbetalingRiver(this, utbetalingMediator)
            AnnulleringRiver(this, producer)
            start()
        }
}
