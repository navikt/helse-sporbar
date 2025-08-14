package no.nav.helse.sporbar

import org.apache.kafka.common.header.internals.RecordHeader

enum class Meldingstype {
    VedtakFattet, VedtakFattetSelvstendigNæringsdrivende, VedtakAnnullert, Behandlingstilstand, Annullering, Utbetaling, UtenUtbetaling, Inntektsmeldingstatus, AvsluttetMedVedtak;

    internal fun header() = RecordHeader("type", name.toByteArray())

}
