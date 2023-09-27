package no.nav.helse.sporbar

import org.apache.kafka.common.header.internals.RecordHeader

enum class Meldingstype {
    VedtakFattet, VedtakAnnullert, Behandlingstilstand, Annullering, Utbetaling, UtenUtbetaling, Inntektsmeldingstatus;

    internal fun header() = RecordHeader("type", name.toByteArray())

}
