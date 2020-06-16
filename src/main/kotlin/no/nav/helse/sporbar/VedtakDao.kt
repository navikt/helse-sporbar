package no.nav.helse.sporbar

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

internal class VedtakDao(private val dataSource: DataSource) {
    internal fun opprett(
        fom: LocalDate,
        tom: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        hendelseIder: List<UUID>,
        vedtak: Vedtak
    ) {
        @Language("PostgreSQL")
        val query =
            """INSERT INTO vedtak(fom, tom, forbrukte_sykedager, gjenstaende_sykedager, vedtaksperiode_id) VALUES (
                       :fom,
                       :tom,
                       :forbrukte_sykedager,
                       :gjenstaende_sykedager,
                       (SELECT distinct vd.vedtaksperiode_id
                        FROM hendelse h
                               INNER JOIN hendelse_dokument hd ON h.id = hd.hendelse_id
                               INNER JOIN dokument d on hd.dokument_id = d.id
                               INNER JOIN vedtak_dokument vd on d.id = vd.dokument_id
                        WHERE h.hendelse_id = ANY ((:hendelseIder)::uuid[])
                        AND d.type = :dokumentType))
                        ON CONFLICT DO NOTHING;
        """

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
                            "fom" to fom,
                            "tom" to tom,
                            "forbrukte_sykedager" to forbrukteSykedager,
                            "gjenstaende_sykedager" to gjenståendeSykedager,
                            "hendelseIder" to hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() },
                            "dokumentType" to Dokument.Type.Sykmelding.name
                        )
                    ).asUpdateAndReturnGeneratedKey
                )

                vedtak.oppdrag.forEach { oppdrag ->
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
