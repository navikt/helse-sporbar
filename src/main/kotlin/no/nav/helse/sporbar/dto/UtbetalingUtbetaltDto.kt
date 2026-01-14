package no.nav.helse.sporbar.dto

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import java.time.LocalDate
import java.util.UUID
import no.nav.helse.sporbar.NULLE_UT_TOMME_OPPDRAG
import no.nav.helse.sporbar.dto.OppdragDto.UtbetalingslinjeDto.Companion.parseLinje
import kotlin.collections.map

private const val gjeldendeVersjon = "1.2.0"

data class UtbetalingUtbetaltDto(
    val utbetalingId: UUID,
    val korrelasjonsId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val stønadsdager: Int,
    val automatiskBehandling: Boolean,
    val arbeidsgiverOppdrag: OppdragDto?,
    val personOppdrag: OppdragDto?,
    val type: String,
    val utbetalingsdager: List<UtbetalingdagDto>,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate
) {
    val event = "utbetaling_utbetalt"
    val versjon = gjeldendeVersjon
    val antallVedtak = 1
}

data class UtbetalingUtenUtbetalingDto(
    val utbetalingId: UUID,
    val korrelasjonsId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val stønadsdager: Int,
    val automatiskBehandling: Boolean,
    val arbeidsgiverOppdrag: OppdragDto?,
    val personOppdrag: OppdragDto?,
    val type: String,
    val utbetalingsdager: List<UtbetalingdagDto>,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate
) {
    val event = "utbetaling_uten_utbetaling"
    val versjon = gjeldendeVersjon
    val antallVedtak = 1
}

enum class BegrunnelseDto {
    AndreYtelserAap,
    AndreYtelserDagpenger,
    AndreYtelserForeldrepenger,
    AndreYtelserOmsorgspenger,
    AndreYtelserOpplaringspenger,
    AndreYtelserPleiepenger,
    AndreYtelserSvangerskapspenger,
    SykepengedagerOppbrukt,
    SykepengedagerOppbruktOver67,
    MinimumInntekt,
    EgenmeldingUtenforArbeidsgiverperiode,
    MinimumSykdomsgrad,
    ManglerOpptjening,
    ManglerMedlemskap,
    EtterDødsdato,
    Over70,
    MinimumInntektOver67,
    //NyVilkårsprøvingNødvendig <- Denne mappes til SykepengedagerOppbrukt i Spleis PGA lang kjedelig historie
}

data class OppdragDto(
    val mottaker: String,
    val fagområde: String,
    val fagsystemId: String,
    val nettoBeløp: Int,
    val stønadsdager: Int,
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalingslinjer: List<UtbetalingslinjeDto>
) {
    companion object {
        fun parseOppdrag(oppdrag: JsonNode) =
            OppdragDto(
                mottaker = oppdrag["mottaker"].asText(),
                fagområde = oppdrag["fagområde"].asText(),
                fagsystemId = oppdrag["fagsystemId"].asText(),
                nettoBeløp = oppdrag["nettoBeløp"].asInt(),
                stønadsdager = oppdrag["stønadsdager"].asInt(),
                fom = oppdrag["fom"].asLocalDate(),
                tom = oppdrag["tom"].asLocalDate(),
                utbetalingslinjer = oppdrag["linjer"].map { linje -> parseLinje(linje) }
            ).takeUnless { NULLE_UT_TOMME_OPPDRAG && it.utbetalingslinjer.isEmpty() }
    }

    data class UtbetalingslinjeDto(
        val fom: LocalDate,
        val tom: LocalDate,
        val dagsats: Int,
        val totalbeløp: Int,
        val grad: Double,
        val stønadsdager: Int
    ) {
        companion object {
            fun parseLinje(linje: JsonNode) =
                UtbetalingslinjeDto(
                    fom = linje["fom"].asLocalDate(),
                    tom = linje["tom"].asLocalDate(),
                    dagsats = linje["sats"].asInt(),
                    totalbeløp = linje["totalbeløp"].asInt(),
                    grad = linje["grad"].asDouble(),
                    stønadsdager = linje["stønadsdager"].asInt()
                )
        }
    }
}
data class UtbetalingdagDto(
    val dato: LocalDate,
    val type: String,
    val begrunnelser: List<BegrunnelseDto>,
    val beløpTilArbeidsgiver: Int,
    val beløpTilSykmeldt: Int,
    val sykdomsgrad: Int
)
