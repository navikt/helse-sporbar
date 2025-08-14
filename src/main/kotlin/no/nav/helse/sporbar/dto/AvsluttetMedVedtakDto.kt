package no.nav.helse.sporbar.dto

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal data class AvsluttetMedVedtakDto(
    val fødselsnummer: String,
    val organisasjonsnummer: String,
    val yrkesaktivitetstype: String,
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val hendelseIder: List<UUID>,
    val skjæringstidspunkt: LocalDate,
    val sykepengegrunnlag: Double,
    val utbetalingId: UUID,
    val vedtakFattetTidspunkt: LocalDateTime,
    val sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaAvsluttetMedVedtak,
    val automatiskBehandling: Boolean,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate,
    val utbetalingsdager: List<Utbetalingsdag>,
)

data class Utbetalingsdag(
    val dato: LocalDate,
    val type: String,
    val beløpTilArbeidsgiver: Int,
    val beløpTilBruker: Int,
    val sykdomsgrad: Int,
    val begrunnelser: List<String>?
)

sealed interface SykepengegrunnlagsfaktaAvsluttetMedVedtak {
    val fastsatt: String
    val omregnetÅrsinntekt: Double
}

data class FastsattEtterHovedregelAvsluttetMedVedtak(
    override val omregnetÅrsinntekt: Double,
    val sykepengegrunnlag: Double,
    val `6G`: Double,
    val arbeidsgivere: List<Arbeidsgiver>,
) : SykepengegrunnlagsfaktaAvsluttetMedVedtak {
    override val fastsatt = "EtterHovedregel"

    data class Arbeidsgiver(
        val arbeidsgiver: String,
        val omregnetÅrsinntekt: Double,
        val inntektskilde: String
    )
}

data class FastsattEtterSkjønnAvsluttetMedVedtak(
    override val omregnetÅrsinntekt: Double,
    val skjønnsfastsatt: Double,
    val sykepengegrunnlag: Double,
    val `6G`: Double,
    val arbeidsgivere: List<Arbeidsgiver>
) : SykepengegrunnlagsfaktaAvsluttetMedVedtak {
    override val fastsatt = "EtterSkjønn"

    data class Arbeidsgiver(
        val arbeidsgiver: String,
        val omregnetÅrsinntekt: Double,
        val skjønnsfastsatt: Double,
        val inntektskilde: String
    )
}

data class FastsattIInfotrygdAvsluttetMedVedtak(
    override val omregnetÅrsinntekt: Double,
) : SykepengegrunnlagsfaktaAvsluttetMedVedtak {
    override val fastsatt = "IInfotrygd"
}

fun JsonNode.sykepengegrunnlagsfaktaAvsluttetMedVedtak(): SykepengegrunnlagsfaktaAvsluttetMedVedtak = when (path("fastsatt").asText()) {
    "EtterHovedregel" -> FastsattEtterHovedregelAvsluttetMedVedtak(
        omregnetÅrsinntekt = path("omregnetÅrsinntekt").asDouble(),
        sykepengegrunnlag = path("sykepengegrunnlag").asDouble(),
        `6G` = path("6G").asDouble(),
        arbeidsgivere = path("arbeidsgivere").map {
            FastsattEtterHovedregelAvsluttetMedVedtak.Arbeidsgiver(
                arbeidsgiver = it["arbeidsgiver"].asText(),
                omregnetÅrsinntekt = it["omregnetÅrsinntekt"].asDouble(),
                inntektskilde = it["inntektskilde"].asText()
            )
        }
    )

    "EtterSkjønn" -> FastsattEtterSkjønnAvsluttetMedVedtak(
        omregnetÅrsinntekt = path("omregnetÅrsinntekt").asDouble(),
        skjønnsfastsatt = path("skjønnsfastsatt").asDouble(),
        sykepengegrunnlag = path("sykepengegrunnlag").asDouble(),
        `6G` = path("6G").asDouble(),
        arbeidsgivere = path("arbeidsgivere").map { arbeidsgiver ->
            FastsattEtterSkjønnAvsluttetMedVedtak.Arbeidsgiver(
                arbeidsgiver = arbeidsgiver["arbeidsgiver"].asText(),
                omregnetÅrsinntekt = arbeidsgiver["omregnetÅrsinntekt"].asDouble(),
                skjønnsfastsatt = arbeidsgiver["skjønnsfastsatt"].asDouble(),
                inntektskilde = arbeidsgiver["inntektskilde"].asText()
            )
        }
    )

    "IInfotrygd" -> FastsattIInfotrygdAvsluttetMedVedtak(
        omregnetÅrsinntekt = path("omregnetÅrsinntekt").asDouble(),
    )

    else -> throw IllegalArgumentException("Ukjent fastsatt-verdi)")
}
