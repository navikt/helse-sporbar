package no.nav.helse.sporbar

import org.apache.kafka.common.header.internals.RecordHeader

enum class VedtakType{
    VedtakFattet, VedtakFattetSelvstendigNæringsdrivende, VedtakAnnullert, Vedtaksdata
}

enum class UtbetalingType {
    Annullering, Utbetaling, UtenUtbetaling
}

fun Enum<*>.header()= RecordHeader("type", name.toByteArray())
