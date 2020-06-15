package no.nav.helse.sporbar

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.util.UUID
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
                       ?,
                       ?,
                       ?,
                       ?,
                       (SELECT distinct vd.vedtaksperiode_id
                        FROM hendelse h
                               INNER JOIN hendelse_dokument hd ON h.id = hd.hendelse_id
                               INNER JOIN dokument d on hd.dokument_id = d.id
                               INNER JOIN vedtak_dokument vd on d.id = vd.dokument_id
                        WHERE h.hendelse_id = ANY ((?)::uuid[])
                        AND d.type = ?))
                        ON CONFLICT DO NOTHING;
        """

        @Language("PostgreSQL")
        val oppdragQuery =
            """INSERT INTO oppdrag(vedtak_id, mottaker, fagomrade, fagsystem_id, totalbeløp) VALUES (?, ?, ?, ?, ?)
        """

        @Language("PostgreSQL")
        val utbetalingQuery = """
            INSERT INTO utbetaling(oppdrag_id, fom, tom, dagsats, grad, belop, sykedager) VALUES (?, ?, ?, ?, ?, ?, ?)
        """
        sessionOf(dataSource, true).use {
            it.transaction { session ->
                val vedtakId = session.run(
                    queryOf(
                        query,
                        fom, tom, forbrukteSykedager, gjenståendeSykedager,
                        hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() },
                        Dokument.Type.Sykmelding.name
                    ).asUpdateAndReturnGeneratedKey
                )

                vedtak.oppdrag.forEach { oppdrag ->
                    val oppdragId = session.run(
                        queryOf(
                            oppdragQuery,
                            vedtakId,
                            oppdrag.mottaker,
                            oppdrag.fagområde,
                            oppdrag.fagsystemId,
                            oppdrag.totalbeløp
                        ).asUpdateAndReturnGeneratedKey
                    )
                    oppdrag.utbetalingslinjer.forEach { linje ->
                        session.run(
                            queryOf(
                                utbetalingQuery,
                                oppdragId,
                                linje.fom,
                                linje.tom,
                                linje.dagsats,
                                linje.grad,
                                linje.beløp,
                                linje.sykedager
                            ).asUpdate
                        )
                    }
                }
            }
        }
    }
}
