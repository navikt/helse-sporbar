package no.nav.helse.sporbar

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

internal class VedtakDao(private val dataSource: DataSource) {
    internal fun opprett(
        utbetaling: Utbetaling
    ) {
        @Language("PostgreSQL")
        val query =
            """INSERT INTO vedtak(fom, tom, forbrukte_sykedager, gjenstaende_sykedager, automatisk_behandling, sykepengegrunnlag, månedsinntekt) VALUES (
                       :fom,
                       :tom,
                       :forbrukte_sykedager,
                       :gjenstaende_sykedager,
                       :automatisk_behandling,
                       :sykepengegrunnlag,
                       :maanedsinntekt)
                        ON CONFLICT DO NOTHING;
        """

        @Language("PostgreSQL")
        val vedtakHendelseQuery =
            """INSERT INTO vedtak_hendelse(vedtak_id, hendelse_id)
                           (SELECT :vedtak_id, h.id
                            FROM hendelse h
                            WHERE h.hendelse_id = ANY ((:hendelse_ider)::uuid[]))
                            ON CONFLICT DO NOTHING;"""

        @Language("PostgreSQL")
        val oppdragQuery =
            """INSERT INTO oppdrag(vedtak_id, mottaker, fagomrade, fagsystem_id, totalbelop) VALUES (
                :vedtak_id,
                :mottaker,
                :fagomrade,
                :fagsystem_id,
                :totalbelop)
        """

        @Language("PostgreSQL")
        val utbetalingQuery = """
            INSERT INTO utbetaling(oppdrag_id, fom, tom, dagsats, grad, belop, sykedager) VALUES (
            :oppdrag_id,
            :fom,
            :tom,
            :dagsats,
            :grad,
            :belop,
            :sykedager)
        """
        sessionOf(dataSource, true).use {
            it.transaction { session ->
                val vedtakId = session.run(
                    queryOf(
                        query,
                        mapOf(
                            "fom" to utbetaling.fom,
                            "tom" to utbetaling.tom,
                            "forbrukte_sykedager" to utbetaling.forbrukteSykedager,
                            "gjenstaende_sykedager" to utbetaling.gjenståendeSykedager,
                            "automatisk_behandling" to utbetaling.automatiskBehandling,
                            "sykepengegrunnlag" to utbetaling.sykepengegrunnlag,
                            "maanedsinntekt" to utbetaling.månedsinntekt
                        )
                    ).asUpdateAndReturnGeneratedKey
                )

                session.run(
                    queryOf(
                        vedtakHendelseQuery,
                        mapOf(
                            "vedtak_id" to vedtakId,
                            "hendelse_ider" to utbetaling.hendelseIder.joinToString(
                                prefix = "{",
                                postfix = "}",
                                separator = ","
                            ) { it.toString() }
                        )
                    ).asUpdate
                )

                utbetaling.oppdrag.forEach { oppdrag ->
                    val oppdragId = session.run(
                        queryOf(
                            oppdragQuery,
                            mapOf(
                                "vedtak_id" to vedtakId,
                                "mottaker" to oppdrag.mottaker,
                                "fagomrade" to oppdrag.fagområde,
                                "fagsystem_id" to oppdrag.fagsystemId,
                                "totalbelop" to oppdrag.totalbeløp
                            )
                        ).asUpdateAndReturnGeneratedKey
                    )
                    oppdrag.utbetalingslinjer.forEach { linje ->
                        session.run(
                            queryOf(
                                utbetalingQuery,
                                mapOf(
                                    "oppdrag_id" to oppdragId,
                                    "fom" to linje.fom,
                                    "tom" to linje.tom,
                                    "dagsats" to linje.dagsats,
                                    "grad" to linje.grad,
                                    "belop" to linje.beløp,
                                    "sykedager" to linje.sykedager
                                )
                            ).asUpdate
                        )
                    }
                }
            }
        }
    }
}
