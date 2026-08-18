package no.nav.helse.sporbar.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class VedtakFattetForHagDto(
    val eventName: String = "vedtak_fattet",
    val fødselsnummer: String,
    val organisasjonsnummer: String,
    val yrkesaktivitetstype: String = "ARBEIDSTAKER",
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val dokumenter: List<DokumentForEksternDto>,
    val sykepengegrunnlag: Double,
    val utbetalingsdager: List<UtbetalingsdagDto>,
    val vedtakFattetTidspunkt: LocalDateTime,
    val vedtaksUtfallTilArbeidsgiver: VedtaksUtfall,
    val saksbehandlerIdent: String?,
    val saksbehandlerNavn: String?,
    val beslutterIdent: String?,
    val beslutterNavn: String?,
    val automatiskFattet: Boolean,
    val harArbeidsgiverØnsketRefusjon: Boolean,
)

// Snakke med noen om dette! :)
enum class VedtaksUtfall {
    AVSLAG,
    DELVIS_INNVILGELSE,
    INNVILGELSE,
}
