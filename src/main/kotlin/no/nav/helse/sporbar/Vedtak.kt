package no.nav.helse.sporbar

import java.time.LocalDate
import java.util.*

internal data class Vedtaksperiode(
    val fnr: String,
    val orgnummer: String,
    val vedtak: Vedtak?,
    val dokumenter: List<Dokument>,
    val tilstand: Tilstand
) {
    internal enum class Tilstand {
        AVVENTER_HISTORIKK,
        AVVENTER_GODKJENNING,
        AVVENTER_SIMULERING,
        TIL_UTBETALING,
        TIL_INFOTRYGD,
        AVSLUTTET,
        AVSLUTTET_UTEN_UTBETALING,
        AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING,
        UTBETALING_FEILET,
        START,
        MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
        MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
        MOTTATT_SYKMELDING_FERDIG_GAP,
        MOTTATT_SYKMELDING_UFERDIG_GAP,
        AVVENTER_SØKNAD_FERDIG_GAP,
        AVVENTER_SØKNAD_UFERDIG_GAP,
        AVVENTER_VILKÅRSPRØVING_GAP,
        AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
        AVVENTER_GAP,
        AVVENTER_INNTEKTSMELDING_FERDIG_GAP,
        AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
        AVVENTER_UFERDIG_GAP,
        AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
        AVVENTER_SØKNAD_UFERDIG_FORLENGELSE,
        AVVENTER_UFERDIG_FORLENGELSE
    }
}



data class Vedtak(
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val oppdrag: List<Oppdrag>
) {
    data class Oppdrag(
        val mottaker: String,
        val fagområde: String,
        val fagsystemId: String,
        val totalbeløp: Int,
        val utbetalingslinjer: List<Utbetalingslinje>
    ) {
        data class Utbetalingslinje(
            val fom: LocalDate,
            val tom: LocalDate,
            val dagsats: Int,
            val beløp: Int,
            val grad: Double,
            val sykedager: Int
        )
    }
}
