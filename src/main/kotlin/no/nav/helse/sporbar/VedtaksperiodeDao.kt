package no.nav.helse.sporbar

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

internal class VedtaksperiodeDao(private val dataSource: DataSource) {
    private class VedtaksperiodeRow(
        val id: Long,
        val fnr: String,
        val orgnummer: String,
        val dokumentId: UUID,
        val dokumentType: Dokument.Type,
        val tilstand: Vedtaksperiode.Tilstand
    )

    private class VedtakRow(
        val vedtaksperiodeId: Long,
        val fom: LocalDate,
        val tom: LocalDate,
        val forbrukteSykedager: Int,
        val gjenståendeSykedager: Int,
        val oppdragRow: OppdragRow
    ) {
        class OppdragRow(
            val oppdragId: Long,
            val mottaker: String,
            val fagområde: String,
            val fagsystemId: String,
            val totalbeløp: Int,
            val utbetalingRow: UtbetalingRow?
        ) {
            class UtbetalingRow(
                val fom: LocalDate,
                val tom: LocalDate,
                val dagsats: Int,
                val grad: Double,
                val beløp: Int,
                val sykedager: Int
            )
        }
    }

    internal fun finn(fødselsnummer: String): List<Vedtaksperiode> {
        @Language("PostgreSQL")
        val query = """SELECT
                           v.*,
                           d.*,
                           (SELECT vt.tilstand FROM vedtak_tilstand vt WHERE vt.vedtaksperiode_id = v.id ORDER BY vt.id DESC LIMIT 1)
                       FROM vedtaksperiode v
                           INNER JOIN vedtak_dokument vd on v.id = vd.vedtaksperiode_id
                           INNER JOIN dokument d on vd.dokument_id = d.id
                       WHERE v.fodselsnummer = ?
                       """

        @Language("PostgreSQL")
        val vedtakQuery = """SELECT vedtak.vedtaksperiode_id,
                                    vedtak.fom vedtakFom,
                                    vedtak.tom vedtakTom,
                                    vedtak.forbrukte_sykedager,
                                    vedtak.gjenstaende_sykedager,
                                    oppdrag.id oppdragId,
                                    oppdrag.mottaker,
                                    oppdrag.fagomrade,
                                    oppdrag.fagsystem_id,
                                    oppdrag.totalbelop,
                                    utbetaling.fom utbetalingFom,
                                    utbetaling.tom utbetalingTom,
                                    utbetaling.dagsats,
                                    utbetaling.grad,
                                    utbetaling.belop,
                                    utbetaling.sykedager
                                 FROM vedtak
                                     INNER JOIN oppdrag on vedtak.id = oppdrag.vedtak_id
                                     LEFT OUTER JOIN utbetaling on oppdrag.id = utbetaling.oppdrag_id
                             WHERE vedtak.vedtaksperiode_id = ANY ((?)::int[])
                       """
        return sessionOf(dataSource)
            .use { session ->
                val vedtaksperioder = session.run(
                    queryOf(query, fødselsnummer)
                        .map { row ->
                            VedtaksperiodeRow(
                                id = row.long("id"),
                                fnr = row.string("fodselsnummer"),
                                orgnummer = row.string("orgnummer"),
                                dokumentId = row.uuid("dokument_id"),
                                dokumentType = enumValueOf(row.string("type")),
                                tilstand = enumValueOf(row.string("tilstand"))
                            )
                        }
                        .asList
                )

                val vedtak = session.run(
                    queryOf(
                        vedtakQuery,
                        vedtaksperioder.map { it.id }.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() }
                    )
                        .map { row ->
                            VedtakRow(
                                vedtaksperiodeId = row.long("vedtaksperiode_id"),
                                fom = row.localDate("vedtakFom"),
                                tom = row.localDate("vedtakTom"),
                                forbrukteSykedager = row.int("forbrukte_sykedager"),
                                gjenståendeSykedager = row.int("gjenstaende_sykedager"),
                                oppdragRow = VedtakRow.OppdragRow(
                                    oppdragId = row.long("oppdragId"),
                                    mottaker = row.string("mottaker"),
                                    fagområde = row.string("fagomrade"),
                                    fagsystemId = row.string("fagsystem_id"),
                                    totalbeløp = row.int("totalbelop"),
                                    utbetalingRow = row.localDateOrNull("utbetalingFom")?.let {
                                        VedtakRow.OppdragRow.UtbetalingRow(
                                            fom = it,
                                            tom = row.localDate("utbetalingTom"),
                                            dagsats = row.int("dagsats"),
                                            grad = row.double("grad"),
                                            beløp = row.int("belop"),
                                            sykedager = row.int("sykedager")
                                        )
                                    }
                                )
                            )
                        }
                        .asList
                ).groupBy { it.vedtaksperiodeId }
                    .mapValues { (_, vedtakValue) ->
                        Vedtak(
                            fom = vedtakValue.first().fom,
                            tom = vedtakValue.first().tom,
                            forbrukteSykedager = vedtakValue.first().forbrukteSykedager,
                            gjenståendeSykedager = vedtakValue.first().gjenståendeSykedager,
                            oppdrag = vedtakValue
                                .map { it.oppdragRow }
                                .groupBy { it.oppdragId }
                                .map { (_, oppdragValue) ->
                                    Vedtak.Oppdrag(
                                        mottaker = oppdragValue.first().mottaker,
                                        fagområde = oppdragValue.first().fagområde,
                                        fagsystemId = oppdragValue.first().fagsystemId,
                                        totalbeløp = oppdragValue.first().totalbeløp,
                                        utbetalingslinjer = oppdragValue
                                            .mapNotNull { it.utbetalingRow }
                                            .map { linjeRow ->
                                                Vedtak.Oppdrag.Utbetalingslinje(
                                                    fom = linjeRow.fom,
                                                    tom = linjeRow.tom,
                                                    dagsats = linjeRow.dagsats,
                                                    grad = linjeRow.grad,
                                                    beløp = linjeRow.beløp,
                                                    sykedager = linjeRow.sykedager
                                                )
                                            }
                                    )
                                }
                        )
                    }

                vedtaksperioder
                    .groupBy { it.id }
                    .map { (vedtaksperiodeId, vedtaksperiodeRows) ->
                        Vedtaksperiode(
                            vedtaksperiodeRows.first().fnr,
                            vedtaksperiodeRows.first().orgnummer,
                            vedtak[vedtaksperiodeId],
                            vedtaksperiodeRows.distinctBy { it.dokumentId }.map { Dokument(it.dokumentId, it.dokumentType) },
                            vedtaksperiodeRows.first().tilstand
                        )
                    }
            }
    }

    internal fun opprett(
        fnr: String,
        orgnummer: String,
        vedtaksperiodeId: UUID,
        hendelseIder: List<UUID>,
        timestamp: LocalDateTime,
        tilstand: Vedtaksperiode.Tilstand
    ) {
        @Language("PostgreSQL")
        val query = """INSERT INTO vedtaksperiode(vedtaksperiode_id, fodselsnummer, orgnummer) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;
                       INSERT INTO vedtak_tilstand(vedtaksperiode_id, sist_endret, tilstand) VALUES ((SELECT id from vedtaksperiode WHERE vedtaksperiode_id = ?), ?, ?);
                       INSERT INTO vedtak_dokument(vedtaksperiode_id, dokument_id)
                           (SELECT
                                   (SELECT id from vedtaksperiode WHERE vedtaksperiode_id = ?),
                                   d.id
                            FROM hendelse h
                                   INNER JOIN hendelse_dokument hd ON h.id = hd.hendelse_id
                                   INNER JOIN dokument d on hd.dokument_id = d.id
                            WHERE h.hendelse_id = ANY ((?)::uuid[]))
                            ON CONFLICT DO NOTHING;
        """
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    vedtaksperiodeId, fnr, orgnummer,
                    vedtaksperiodeId, timestamp, tilstand.name,
                    vedtaksperiodeId,
                    hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() }
                ).asExecute
            )
        }
    }
}
