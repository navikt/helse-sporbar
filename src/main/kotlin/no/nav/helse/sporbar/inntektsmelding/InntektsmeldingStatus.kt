package no.nav.helse.sporbar.inntektsmelding

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID
import no.nav.helse.rapids_rivers.JsonMessage

internal interface InntektsmeldingStatus {
    val id: UUID
    val hendelseId: UUID
    val fødselsnummer: String
    val aktørId: String
    val organisasjonsnummer: String
    val vedtaksperiodeId: UUID
    val fom: LocalDate
    val tom: LocalDate
    val opprettet: LocalDateTime
    val json: JsonMessage
}

internal class BehandlesUtenforSpleis(
    override val id: UUID,
    override val hendelseId: UUID,
    override val fødselsnummer: String,
    override val aktørId: String,
    override val organisasjonsnummer: String,
    override val vedtaksperiodeId: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val opprettet: LocalDateTime,
    override val json: JsonMessage
) : InntektsmeldingStatus

internal class ManglerInntektsmelding(
    override val id: UUID,
    override val hendelseId: UUID,
    override val fødselsnummer: String,
    override val aktørId: String,
    override val organisasjonsnummer: String,
    override val vedtaksperiodeId: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val opprettet: LocalDateTime,
    override val json: JsonMessage
) : InntektsmeldingStatus

internal class HarInntektsmelding(
    override val id: UUID,
    override val hendelseId: UUID,
    override val fødselsnummer: String,
    override val aktørId: String,
    override val organisasjonsnummer: String,
    override val vedtaksperiodeId: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val opprettet: LocalDateTime,
    override val json: JsonMessage
) : InntektsmeldingStatus

internal class TrengerIkkeInntektsmelding(
    override val id: UUID,
    override val hendelseId: UUID,
    override val fødselsnummer: String,
    override val aktørId: String,
    override val organisasjonsnummer: String,
    override val vedtaksperiodeId: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val opprettet: LocalDateTime,
    override val json: JsonMessage
) : InntektsmeldingStatus

internal data class InntektsmeldingStatusForEksternDto(
    val id: UUID,
    val sykmeldt: String,
    val arbeidsgiver: String,
    val vedtaksperiode: VedtaksperiodeForEksternDto,
    val tidspunkt: ZonedDateTime,
    val status: String
) {
    val versjon = "1.0.0"
    data class VedtaksperiodeForEksternDto(
        val id: UUID,
        val fom: LocalDate,
        val tom: LocalDate
    )
}