package no.nav.helse.sporbar.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class VedtakFattetSelvstendigNæringsdrivendeDto(
    val fødselsnummer: String,
    val aktørId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val sykepengegrunnlag: BigDecimal,
    val utbetalingId: UUID,
    val vedtakFattetTidspunkt: Instant,
    val sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaSelvstendigDto,
    val begrunnelser: List<BegrunnelseSelvstendigDto>,
)

data class BegrunnelseSelvstendigDto(
    val type: String,
    val begrunnelse: String,
    val perioder: List<PeriodeSelvstendigDto>
)

data class PeriodeSelvstendigDto(
    val fom: LocalDate,
    val tom: LocalDate
)

data class PersonsinntektDto(val år: Int, val inntekt: BigDecimal)

data class SykepengegrunnlagsfaktaSelvstendigDto(
    val personinntekter: List<PersonsinntektDto>,
)
