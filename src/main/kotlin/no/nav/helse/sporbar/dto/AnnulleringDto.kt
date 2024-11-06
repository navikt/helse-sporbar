package no.nav.helse.sporbar.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class AnnulleringDto(
    val utbetalingId: UUID,
    val korrelasjonsId: UUID,
    val organisasjonsnummer: String,
    val tidsstempel: LocalDateTime,
    val fødselsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val arbeidsgiverFagsystemId: String?,
    val personFagsystemId: String?) {
    val event = "utbetaling_annullert"
    @Deprecated("trengs så lenge vi produserer til on-prem")
    val orgnummer: String = organisasjonsnummer
}