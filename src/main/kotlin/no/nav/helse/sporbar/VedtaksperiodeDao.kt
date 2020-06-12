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
        val vedtaksperiodeId: UUID,
        val fnr: String,
        val orgnummer: String,
        val dokumentId: UUID,
        val dokumentType: Dokument.Type,
        val tilstand: Vedtaksperiode.Tilstand,
        val fom: LocalDate?,
        val tom: LocalDate?,
        val forbrukteSykedager: Int?,
        val gjenståendeSykedager: Int?
    )

    internal fun finn(fødselsnummer: String): List<Vedtaksperiode> {
        @Language("PostgreSQL")
        val query = """SELECT
                           v.*,
                           d.*,
                           (SELECT vt.tilstand FROM vedtak_tilstand vt WHERE vt.vedtaksperiode_id = v.id ORDER BY vt.id DESC LIMIT 1),
                           v2.*
                       FROM vedtaksperiode v
                           INNER JOIN vedtak_dokument vd on v.id = vd.vedtaksperiode_id
                           INNER JOIN dokument d on vd.dokument_id = d.id
                           LEFT OUTER JOIN vedtak v2 on v.id = v2.vedtaksperiode_id
                       WHERE v.fodselsnummer = ?
                       """
        return sessionOf(dataSource)
            .use { session ->
                session.run(
                    queryOf(query, fødselsnummer)
                        .map { row ->
                            VedtaksperiodeRow(
                                vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                                fnr = row.string("fodselsnummer"),
                                orgnummer = row.string("orgnummer"),
                                dokumentId = row.uuid("dokument_id"),
                                dokumentType = enumValueOf(row.string("type")),
                                tilstand = enumValueOf(row.string("tilstand")),
                                fom = row.localDateOrNull("fom"),
                                tom = row.localDateOrNull("tom"),
                                forbrukteSykedager = row.intOrNull("forbrukte_sykedager"),
                                gjenståendeSykedager = row.intOrNull("gjenstaende_sykedager")
                            )
                        }
                        .asList
                )
            }
            .groupBy { it.vedtaksperiodeId }
            .map { entry ->
                Vedtaksperiode(
                    entry.key,
                    entry.value.first().fnr,
                    entry.value.first().orgnummer,
                    entry.value.first().takeIf { it.fom != null }?.let { Vedtak(it.fom!!, it.tom!!, it.forbrukteSykedager!!, it.gjenståendeSykedager!!, emptyList()) },
                    entry.value.distinctBy { it.dokumentId }.map { Dokument(it.dokumentId, it.dokumentType) },
                    entry.value.first().tilstand
                )
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
