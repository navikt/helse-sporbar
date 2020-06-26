package no.nav.helse.sporbar

import java.time.LocalDate

data class VedtakDto(
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val utbetalinger: List<UtbetalingDto>,
    val dokumenter: List<Dokument>
) {
    data class UtbetalingDto(
        val mottaker: String,
        val fagområde: String,
        val totalbeløp: Int,
        val utbetalingslinjer: List<UtbetalingslinjeDto>
    ) {
        data class UtbetalingslinjeDto(
            val fom: LocalDate,
            val tom: LocalDate,
            val dagsats: Int,
            val beløp: Int,
            val grad: Double,
            val sykedager: Int
        )
    }
}
