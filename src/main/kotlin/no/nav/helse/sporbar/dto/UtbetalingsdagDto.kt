package no.nav.helse.sporbar.dto

import java.time.LocalDate
import java.util.UUID

data class UtbetalingsdagDto(
    val dato: LocalDate,
    val type: String,
    val sykdomsgrad: Int,
    val dekningsgrad: Int,
    val beløpTilBruker: Int,
    val beløpTilArbeidsgiver: Int,
    val forespurtRefusjonsbeløp: Int,
    val kilde: KildeDto?,
    val begrunnelser: List<String>,
)

data class KildeDto(
    val avsender: String,
    val referanse: UUID,
)
