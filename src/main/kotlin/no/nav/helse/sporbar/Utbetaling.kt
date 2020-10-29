package no.nav.helse.sporbar

import java.time.LocalDate
import java.util.UUID

data class Utbetaling(
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val automatiskBehandling: Boolean,
    val hendelseIder: List<UUID>,
    val oppdrag: List<Oppdrag>,
    val sykepengegrunnlag: Double,
    val månedsinntekt: Double
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
