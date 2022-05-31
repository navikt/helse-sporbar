package no.nav.helse.sporbar.inntektsmelding

import javax.sql.DataSource

class InntektsmeldingDao(private val dataSource: DataSource) {

    internal fun trengerInntektsmelding(melding: InntektsmeldingPakke) {
        // TODO: dump i en tabell
    }

    internal fun trengerIkkeInntektsmelding(melding: InntektsmeldingPakke) {
        // TODO: dump i en tabell
    }
}