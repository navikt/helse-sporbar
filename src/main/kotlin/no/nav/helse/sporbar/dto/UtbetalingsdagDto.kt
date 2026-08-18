package no.nav.helse.sporbar.dto

import java.time.LocalDate

data class UtbetalingsdagDto(
    val dato: LocalDate,
    val type: String,
    val sykdomsgrad: Int,
    val dekningsgrad: Int,
    val beløpTilBruker: Int,
    val beløpTilArbeidsgiver: Int,
    val begrunnelser: List<String>,
)
