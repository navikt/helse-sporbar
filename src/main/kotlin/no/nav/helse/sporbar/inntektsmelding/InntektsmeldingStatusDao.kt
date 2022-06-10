package no.nav.helse.sporbar.inntektsmelding

import java.time.Duration
import java.time.LocalDateTime
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sporbar.uuid
import org.intellij.lang.annotations.Language

internal class InntektsmeldingStatusDao(
    private val dataSource: DataSource
) {
    internal fun hent(statusTimeout: Duration): List<InntektsmeldingStatusForEksternDto> {
        @Language("PostgreSQL")
        val sql = """
            SELECT DISTINCT ON(vedtaksperiode_id) vedtaksperiode_id, id, fom, tom, fodselsnummer, orgnummer, status, hendelse_opprettet 
            FROM inntektsmelding_status 
            WHERE melding_publisert IS NULL AND melding_ignorert IS NULL AND melding_innsatt < now() - INTERVAL '${statusTimeout.seconds} SECONDS' 
            ORDER BY vedtaksperiode_id, melding_innsatt DESC
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
                    tidspunkt = row.localDateTime("hendelse_opprettet")
                )
            }.asList)
        }
    }

    internal fun publisert(statuser: List<InntektsmeldingStatusForEksternDto>) {
        if (statuser.isEmpty()) return
        val now = LocalDateTime.now()
        val ider = statuser.map { it.id }
        val vedtaksperiodeIder = statuser.map { it.vedtaksperiode.id     }
        sessionOf(dataSource).use { session ->
            session.run(queryOf("UPDATE inntektsmelding_status SET melding_publisert = ? WHERE id IN (${ider.joinToString { "?" }})", now, *ider.toTypedArray()).asUpdate)
            session.run(queryOf("UPDATE inntektsmelding_status SET melding_ignorert = ? WHERE id NOT IN (${ider.joinToString { "?" }}) AND vedtaksperiode_id IN (${vedtaksperiodeIder.joinToString { "?" }})", now, *ider.toTypedArray(), *vedtaksperiodeIder.toTypedArray()).asUpdate)
        }
    }

    internal fun lagre(inntektsmeldingStatus: InntektsmeldingStatus) {
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