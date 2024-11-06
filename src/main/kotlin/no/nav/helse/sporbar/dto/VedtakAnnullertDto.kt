package no.nav.helse.sporbar.dto

import java.time.LocalDate
import java.util.UUID

data class VedtakAnnullertDto(
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate
) {
    val event: String = "vedtak_annullert"
    val versjon: String = "1.0.0"
}