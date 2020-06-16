package no.nav.helse.sporbar

import no.nav.helse.rapids_rivers.RapidApplication
import org.apache.kafka.clients.producer.KafkaProducer

fun main() {
    val env = Environment(System.getenv())
    launchApplication(env)
}

fun launchApplication(env: Environment) {
    val dataSource = DataSourceBuilder(env.db)
        .apply(DataSourceBuilder::migrate)
        .getDataSource()

    val dokumentDao = DokumentDao(dataSource)
    val producer =
        KafkaProducer<String, VedtaksperiodeDto>(
            loadBaseConfig(
                env.raw.getValue("KAFKA_BOOTSTRAP_SERVERS"),
                env.serviceUser
            ).toProducerConfig()
        )
    val vedtaksperiodeDao = VedtaksperiodeDao(dataSource)
    val mediator = VedtaksperiodeMediator(vedtaksperiodeDao, producer)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .build().apply {
            start()
        }
}
