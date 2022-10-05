package no.nav.helse.sporbar.inntektsmelding

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sporbar.uuid
import org.flywaydb.core.internal.database.postgresql.PostgreSQLAdvisoryLockTemplate
import org.intellij.lang.annotations.Language

internal interface InntektsmeldingStatusDao {
    fun lagre(inntektsmeldingStatus: InntektsmeldingStatus)
    fun hent(statustimeout: Duration): List<InntektsmeldingStatusForEksternDto>
    fun publisert(statuser: List<InntektsmeldingStatusForEksternDto>)
    fun erBehandletUtenforSpleis(vedtaksperiodeId: UUID): Boolean
}

internal class PostgresInntektsmeldingStatusDao(
    private val dataSource: DataSource
): InntektsmeldingStatusDao {

    override fun erBehandletUtenforSpleis(vedtaksperiodeId: UUID): Boolean {
        @Language("PostgreSQL")
        val sql = """
            SELECT 1 FROM inntektsmelding_status
            WHERE vedtaksperiode_id = :vedtaksperiodeId AND status = 'BEHANDLES_UTENFOR_SPLEIS'
        """
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(sql, mapOf("vedtaksperiodeId" to vedtaksperiodeId)).map { it }.asList).isNotEmpty()
        }
    }

    override fun hent(statustimeout: Duration): List<InntektsmeldingStatusForEksternDto> {
        @Language("PostgreSQL")
        val sql = """
            SELECT * FROM (
                SELECT DISTINCT ON(vedtaksperiode_id) vedtaksperiode_id, id, fom, tom, fodselsnummer, orgnummer, status, hendelse_opprettet, melding_innsatt 
                FROM inntektsmelding_status 
                WHERE melding_publisert IS NULL AND melding_ignorert IS NULL
                ORDER BY vedtaksperiode_id, melding_innsatt DESC
            ) AS potensielle 
            WHERE potensielle.melding_innsatt < now() - INTERVAL '${statustimeout.seconds} SECONDS'
            LIMIT 500
        """
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(sql).map { row ->
                InntektsmeldingStatusForEksternDto(
                    id = row.uuid("id"),
                    vedtaksperiode = InntektsmeldingStatusForEksternDto.VedtaksperiodeForEksternDto(
                        id = row.uuid("vedtaksperiode_id"),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                    ),
                    sykmeldt = row.string("fodselsnummer"),
                    arbeidsgiver = row.string("orgnummer"),
                    status = row.string("status"),
                    tidspunkt = ZonedDateTime.of(row.localDateTime("hendelse_opprettet"), ZoneId.of("Europe/Oslo"))
                )
            }.asList)
        }
    }

    override fun publisert(statuser: List<InntektsmeldingStatusForEksternDto>) {
        if (statuser.isEmpty()) return
        val now = LocalDateTime.now()
        val ider = statuser.map { it.id }
        val vedtaksperiodeIder = statuser.map { it.vedtaksperiode.id }
        sessionOf(dataSource).use { session ->
            session.transaction { transactionalSession ->
                @Language("PostgreSQL")
                val meldingPublisertSql = """
                    UPDATE inntektsmelding_status SET melding_publisert = ? 
                    WHERE id IN (${ider.joinToString { "?" }})
                """
                transactionalSession.run(queryOf(meldingPublisertSql, now, *ider.toTypedArray()).asUpdate)
                @Language("PostgreSQL")
                val meldingIgnorertSql = """
                    UPDATE inntektsmelding_status SET melding_ignorert = ? 
                    WHERE id NOT IN (${ider.joinToString { "?" }}) 
                    AND vedtaksperiode_id IN (${vedtaksperiodeIder.joinToString { "?" }})
                """
                transactionalSession.run(queryOf(meldingIgnorertSql, now, *ider.toTypedArray(), *vedtaksperiodeIder.toTypedArray()).asUpdate)
            }
        }
    }

    override fun lagre(inntektsmeldingStatus: InntektsmeldingStatus) {
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO inntektsmelding_status (id, hendelse_id, hendelse_opprettet, fodselsnummer, orgnummer, vedtaksperiode_id, fom, tom, status, data)  " +
                            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING",
                    inntektsmeldingStatus.id,
                    inntektsmeldingStatus.hendelseId,
                    inntektsmeldingStatus.opprettet,
                    inntektsmeldingStatus.fødselsnummer,
                    inntektsmeldingStatus.organisasjonsnummer,
                    inntektsmeldingStatus.vedtaksperiodeId,
                    inntektsmeldingStatus.fom,
                    inntektsmeldingStatus.tom,
                    inntektsmeldingStatus.status(),
                    inntektsmeldingStatus.json.toJson()
                ).asUpdate
            )
        }
    }

    private fun InntektsmeldingStatus.status() = when (this) {
        is ManglerInntektsmelding -> "MANGLER_INNTEKTSMELDING"
        is HarInntektsmelding -> "HAR_INNTEKTSMELDING"
        is TrengerIkkeInntektsmelding -> "TRENGER_IKKE_INNTEKTSMELDING"
        is BehandlesUtenforSpleis -> "BEHANDLES_UTENFOR_SPLEIS"
        else -> throw IllegalStateException("Mangler status for ${javaClass.simpleName}")
    }
}