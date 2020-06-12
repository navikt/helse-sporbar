package no.nav.helse.sporbar

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

internal class VedtaksperiodeDao(private val dataSource: DataSource) {
    private class VedtaksperiodeRow(
        val vedtaksperiodeId: UUID,
        val fnr: String,
        val orgnummer: String,
        val dokumentId: UUID,
        val dokumentType: Dokument.Type
    )

    internal fun finn(fødselsnummer: String): List<Vedtaksperiode> {
        @Language("PostgreSQL")
        val query = """SELECT v.*, d.*
                       FROM vedtaksperiode v
                           INNER JOIN vedtak_dokument vd on v.id = vd.vedtaksperiode_id
                           INNER JOIN dokument d on vd.dokument_id = d.id
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
                                dokumentType = enumValueOf(row.string("type"))
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
                    null,
                    entry.value.map { Dokument(it.dokumentId, it.dokumentType) }
                )
            }
    }

    internal fun opprett(
        fnr: String,
        orgnummer: String,
        vedtaksperiodeId: UUID,
        hendelseIder: List<UUID>
    ) {
        @Language("PostgreSQL")
        val query = """INSERT INTO vedtaksperiode(vedtaksperiode_id, fodselsnummer, orgnummer) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;
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
                    vedtaksperiodeId,
                    fnr,
                    orgnummer,
                    vedtaksperiodeId,
                    hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() }
                ).asExecute
            )
        }
    }
}
