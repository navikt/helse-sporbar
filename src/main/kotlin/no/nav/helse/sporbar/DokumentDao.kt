package no.nav.helse.sporbar

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

internal class DokumentDao(private val datasourceProvider: () -> DataSource) {
    internal fun opprett(hendelseId: UUID, opprettet: LocalDateTime, dokumentId: UUID, type: Dokument.Type) {
        @Language("PostgreSQL")
        val query = """
            INSERT INTO hendelse(hendelse_id, timestamp) VALUES(?,?) ON CONFLICT DO NOTHING;
            INSERT INTO dokument(dokument_id, type) VALUES(?,?) ON CONFLICT DO NOTHING;
            INSERT INTO hendelse_dokument(hendelse_id, dokument_id)
            VALUES(
                (SELECT h.id FROM hendelse h WHERE h.hendelse_id = ?),
                (SELECT d.id FROM dokument d WHERE d.dokument_id = ?))
            ON CONFLICT DO NOTHING;"""
        sessionOf(datasourceProvider()).use {
            it.transaction { session ->
                session.run(
                    queryOf(
                        query,
                        hendelseId,
                        opprettet,
                        dokumentId,
                        type.name,
                        hendelseId,
                        dokumentId
                    ).asUpdate
                )
            }
        }
    }

    internal fun finn(hendelseIder: List<UUID>) = sessionOf(datasourceProvider()).use { session ->
        @Language("PostgreSQL")
        val query = """SELECT distinct d.dokument_id, d.type
                       FROM hendelse h
                                INNER JOIN hendelse_dokument hd ON h.id = hd.hendelse_id
                                INNER JOIN dokument d ON hd.dokument_id = d.id
                       WHERE h.hendelse_id = ANY ((?)::uuid[])"""
        session.run(
            queryOf(query, hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() })
                .map { row -> Dokument(row.uuid("dokument_id"), enumValueOf(row.string("type"))) }
                .asList
        )
    }
}

fun Row.uuid(columnLabel: String): UUID = UUID.fromString(string(columnLabel))
