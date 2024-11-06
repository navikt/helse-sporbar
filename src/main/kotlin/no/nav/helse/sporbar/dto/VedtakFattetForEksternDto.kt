package no.nav.helse.sporbar.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class VedtakFattetForEksternDto(
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val skjæringstidspunkt: LocalDate,
    val dokumenter: List<DokumentForEkstern>,
    val inntekt: Double,
    val sykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlag: Double,
    val grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Double>,
    val begrensning: String,
    val utbetalingId: UUID?,
    val vedtakFattetTidspunkt: LocalDateTime,
    val sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaForEksternDto?,
    val begrunnelser: List<BegrunnelseForEksternDto>,
    val tags: Set<String>
) {
    val versjon: String = "1.2.0"
}

class DokumentForEkstern(val dokumentId: UUID, val type: Type) {
    enum class Type {
        Sykmelding, Søknad, Inntektsmelding
    }
}

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
): SykepengegrunnlagsfaktaForEksternDto() {
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
): SykepengegrunnlagsfaktaForEksternDto() {
    data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double, val skjønnsfastsatt: Double)
}

data class FastsattIInfotrygdForEksternDto(
    val fastsatt: String,
    val omregnetÅrsinntekt: Double,
) : SykepengegrunnlagsfaktaForEksternDto()


