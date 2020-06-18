package no.nav.helse.sporbar

import java.time.LocalDate

data class Vedtak(
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val oppdrag: List<Oppdrag> //Oppdrag kan kanskje hete en utbetaling
) {
    data class Oppdrag(
        val mottaker: String,
        val fagområde: String,
        val fagsystemId: String, //TODO: Denne skal kanskje bort? Vi vil ikke at noen binder seg til den..
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
