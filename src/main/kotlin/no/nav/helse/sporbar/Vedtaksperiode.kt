package no.nav.helse.sporbar

import java.util.UUID

internal data class Vedtaksperiode(
    val fnr: String,
    val orgnummer: String,
    val dokumenter: List<Dokument>,
    val tilstand: Tilstand,
    val vedtaksperiodeId: UUID
) {
    internal enum class Tilstand {
        AVVENTER_HISTORIKK,
        AVVENTER_GODKJENNING,
        AVVENTER_SIMULERING,
        TIL_UTBETALING,
        TIL_INFOTRYGD,
        AVSLUTTET,
        AVSLUTTET_UTEN_UTBETALING,
        @Deprecated(":)")
        AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING,
        UTEN_UTBETALING_MED_INNTEKTSMELDING_UFERDIG_GAP,
        UTEN_UTBETALING_MED_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
        UTBETALING_FEILET,
        START,
        MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
        MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
        MOTTATT_SYKMELDING_FERDIG_GAP,
        MOTTATT_SYKMELDING_UFERDIG_GAP,
        AVVENTER_SØKNAD_FERDIG_GAP,
        AVVENTER_ARBEIDSGIVERSØKNAD_FERDIG_GAP,
        AVVENTER_ARBEIDSGIVERSØKNAD_UFERDIG_GAP,
        AVVENTER_SØKNAD_UFERDIG_GAP,
        @Deprecated("Erstattes av AVVENTER_VILKÅRSPRØVING")
        AVVENTER_VILKÅRSPRØVING_GAP,
        AVVENTER_VILKÅRSPRØVING,
        @Deprecated(":)")
        AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
        AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
        AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
        AVVENTER_INNTEKTSMELDING_FERDIG_FORLENGELSE,
        AVVENTER_UFERDIG_GAP,
        AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
        AVVENTER_SØKNAD_UFERDIG_FORLENGELSE,
        AVVENTER_SØKNAD_FERDIG_FORLENGELSE,
        AVVENTER_UFERDIG_FORLENGELSE,
        AVVENTER_ARBEIDSGIVERE
    }
}
