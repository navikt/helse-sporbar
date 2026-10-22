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
    val forespurtRefusjon: ForespurtRefusjonDto,
    val begrunnelser: List<String>,
)

data class ForespurtRefusjonDto(
    val beløp: Int,
    val avsender: String,
    val sasInntektsmeldingReferanse: UUID?,
    val hagInntektsmeldingReferanse: UUID?,
)
