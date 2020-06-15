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
        val vedtakId: Long,
        val vedtaksperiodeId: Long,
        val fom: LocalDate,
        val tom: LocalDate,
        val forbrukteSykedager: Int,
        val gjenståendeSykedager: Int,
        val mottaker: String,
        val fagområde: String,
        val fagsystemId: String,
        val totalbeløp: Int
    )

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
        val vedtakQuery = """SELECT *, v.id vedtakId
                                 FROM vedtak v
                                     INNER JOIN oppdrag o on v.id = o.vedtak_id
                             WHERE vedtaksperiode_id = ANY ((?)::int[])
                       """
        return sessionOf(dataSource)
            .use { session ->
                val vedtaksperioder2 = session.run(
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
                        vedtaksperioder2.map { it.id }.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() }
                    )
                        .map { row ->
                            VedtakRow(
                                vedtakId = row.long("vedtakId"),
                                vedtaksperiodeId = row.long("vedtaksperiode_id"),
                                fom = row.localDate("fom"),
                                tom = row.localDate("tom"),
                                forbrukteSykedager = row.int("forbrukte_sykedager"),
                                gjenståendeSykedager = row.int("gjenstaende_sykedager"),
                                mottaker = row.string("mottaker"),
                                fagområde = row.string("fagomrade"),
                                fagsystemId = row.string("fagsystem_id"),
                                totalbeløp = row.int("totalbeløp")
                            )
                        }
                        .asList
                ).groupBy { it.vedtakId }
                    .mapKeys { it.value.first().vedtaksperiodeId }
                    .mapValues { entry ->
                        Vedtak(
                            fom = entry.value.first().fom,
                            tom = entry.value.first().tom,
                            forbrukteSykedager = entry.value.first().forbrukteSykedager,
                            gjenståendeSykedager = entry.value.first().gjenståendeSykedager,
                            oppdrag = entry.value.map { oppdragRow ->
                                Vedtak.Oppdrag(
                                    mottaker = oppdragRow.mottaker,
                                    fagområde = oppdragRow.fagområde,
                                    fagsystemId = oppdragRow.fagsystemId,
                                    totalbeløp = oppdragRow.totalbeløp,
                                    utbetalingslinjer = emptyList()
                                )
                            }
                        )
                    }

                val vedtaksperioder = vedtaksperioder2
                    .groupBy { it.id }
                    .map { entry ->
                        Vedtaksperiode(
                            entry.value.first().fnr,
                            entry.value.first().orgnummer,
                            vedtak[entry.key],
                            entry.value.distinctBy { it.dokumentId }.map { Dokument(it.dokumentId, it.dokumentType) },
                            entry.value.first().tilstand
                        )
                    }



                vedtaksperioder
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
