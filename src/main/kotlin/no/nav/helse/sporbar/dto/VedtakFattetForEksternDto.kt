package no.nav.helse.sporbar.dto

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class VedtakFattetForEksternDto(
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val yrkesaktivitetstype: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val dokumenter: List<DokumentForEkstern>,
    val sykepengegrunnlag: Double,
    val utbetalingId: UUID,
    val vedtakFattetTidspunkt: LocalDateTime,
    val sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaForEksternDto,
    val begrunnelser: List<BegrunnelseForEksternDto>,
    val tags: Set<String>,
    val saksbehandler: NavnOgIdentForEksternDto?,
    val beslutter: NavnOgIdentForEksternDto?,
) {
    val versjon: String = "1.2.2"

    @Deprecated("denne verdien aner vi ikke om brukes av noen, og utregningen er jo også ganske suspekt")
    val begrensning: String = when (sykepengegrunnlagsfakta) {
        is FastsattEtterSkjønnForEksternDto -> if (sykepengegrunnlagsfakta.skjønnsfastsatt > sykepengegrunnlagsfakta.`6G`)
            "ER_6G_BEGRENSET"
        else
            "ER_IKKE_6G_BEGRENSET"

        is FastsattEtterHovedregelForEksternDto -> if (sykepengegrunnlagsfakta.omregnetÅrsinntekt > sykepengegrunnlagsfakta.`6G`)
            "ER_6G_BEGRENSET"
        else
            "ER_IKKE_6G_BEGRENSET"

        is FastsattIInfotrygdForEksternDto -> "VURDERT_I_INFOTRYGD"

        is SykepengegrunnlagsfaktaSelvstendigDto -> if (sykepengegrunnlagsfakta.selvstendig.beregningsgrunnlag > sykepengegrunnlagsfakta.`6G`)
            "ER_6G_BEGRENSET"
        else
            "ER_IKKE_6G_BEGRENSET"
    }

    @Deprecated("denne verdien aner vi ikke om brukes av noen, og utregningen er jo også ganske suspekt")
    val inntekt = when (sykepengegrunnlagsfakta) {
        is FastsattEtterSkjønnForEksternDto -> sykepengegrunnlagsfakta.skjønnsfastsatt
        is FastsattEtterHovedregelForEksternDto -> sykepengegrunnlagsfakta.omregnetÅrsinntekt
        is FastsattIInfotrygdForEksternDto -> sykepengegrunnlagsfakta.omregnetÅrsinntekt
        is SykepengegrunnlagsfaktaSelvstendigDto -> sykepengegrunnlagsfakta.selvstendig.beregningsgrunnlag.toDouble()
    } / 12.0

    @Deprecated("denne verdien aner vi ikke om brukes av noen, og utregningen er jo også ganske suspekt")
    val grunnlagForSykepengegrunnlag: Double = when (sykepengegrunnlagsfakta) {
        is FastsattEtterSkjønnForEksternDto -> sykepengegrunnlagsfakta.skjønnsfastsatt
        is FastsattEtterHovedregelForEksternDto -> sykepengegrunnlagsfakta.omregnetÅrsinntekt
        is FastsattIInfotrygdForEksternDto -> sykepengegrunnlagsfakta.omregnetÅrsinntekt
        is SykepengegrunnlagsfaktaSelvstendigDto -> sykepengegrunnlagsfakta.selvstendig.beregningsgrunnlag.toDouble()
    }

    @Deprecated("denne verdien aner vi ikke om brukes av noen, og utregningen er jo også ganske suspekt")
    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double> = when (sykepengegrunnlagsfakta) {
        is FastsattEtterSkjønnForEksternDto -> sykepengegrunnlagsfakta
            .arbeidsgivere
            .associate { it.arbeidsgiver to it.skjønnsfastsatt.toDesimaler }

        is FastsattEtterHovedregelForEksternDto -> sykepengegrunnlagsfakta
            .arbeidsgivere
            .associate { it.arbeidsgiver to it.omregnetÅrsinntekt.toDesimaler }

        is FastsattIInfotrygdForEksternDto -> emptyMap()

        is SykepengegrunnlagsfaktaSelvstendigDto -> mapOf(
            organisasjonsnummer to sykepengegrunnlagsfakta.selvstendig.beregningsgrunnlag.toDouble()
        )
    }
}

private val Double.toDesimaler get() = toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()

class DokumentForEkstern(val dokumentId: UUID, val type: Type) {
    enum class Type {
        Sykmelding, Søknad
    }
}

data class NavnOgIdentForEksternDto(
    val navn: String,
    val ident: String,
)

data class BegrunnelseForEksternDto(
    val type: String,
    val begrunnelse: String,
    val perioder: List<PeriodeForEksternDto>
)

data class PeriodeForEksternDto(
    val fom: LocalDate,
    val tom: LocalDate
)

sealed class SykepengegrunnlagsfaktaForEksternDto

data class FastsattEtterHovedregelForEksternDto(
    val fastsatt: String,
    val omregnetÅrsinntekt: Double,
    val innrapportertÅrsinntekt: Double,
    val avviksprosent: Double,
    val `6G`: Double,
    val tags: Set<String>,
    val arbeidsgivere: List<Arbeidsgiver>
) : SykepengegrunnlagsfaktaForEksternDto() {
    data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double)
}

data class FastsattEtterSkjønnForEksternDto(
    val fastsatt: String,
    val omregnetÅrsinntekt: Double,
    val innrapportertÅrsinntekt: Double,
    val skjønnsfastsatt: Double,
    val avviksprosent: Double,
    val `6G`: Double,
    val tags: Set<String>,
    val arbeidsgivere: List<Arbeidsgiver>
) : SykepengegrunnlagsfaktaForEksternDto() {
    data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double, val skjønnsfastsatt: Double)
}

data class FastsattIInfotrygdForEksternDto(
    val fastsatt: String,
    val omregnetÅrsinntekt: Double,
) : SykepengegrunnlagsfaktaForEksternDto()

data class SykepengegrunnlagsfaktaSelvstendigDto(
    val fastsatt: String,
    val `6G`: BigDecimal,
    val tags: Set<String>,
    val selvstendig: Selvstendig,
) : SykepengegrunnlagsfaktaForEksternDto() {
    data class Selvstendig(
        val beregningsgrunnlag: BigDecimal,
        val pensjonsgivendeInntekter: List<PensjonsgivendeInntekt>
    ) {
        data class PensjonsgivendeInntekt (
            val årstall: Int,
            val beløp: BigDecimal,
        )
    }
}
