package no.nav.helse.sporbar

import no.nav.helse.rapids_rivers.RapidApplication

fun main() {
    val env = Environment(System.getenv())
    launchApplication(env)
}

fun launchApplication(env: Environment) {
    val dataSource = DataSourceBuilder(env.db)
        .apply(DataSourceBuilder::migrate)
        .getDataSource()

    val dokumentDao = DokumentDao(dataSource)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(env.raw))
        .build().apply {
            start()
        }
}
