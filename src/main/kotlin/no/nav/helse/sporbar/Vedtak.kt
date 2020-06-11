package no.nav.helse.sporbar

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal data class Vedtaksperiode(
    val vedtaksperiodeId: UUID,
    val fnr: String,
    val orgnummer: String,
    val opprettet: LocalDateTime,
    val vedtak: Vedtak?,
    val dokumenter: List<Dokument>
)

enum class VedtaksperiodeTilstand {
    Tilstand
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
