package no.nav.helse.sporbar.inntektsmelding

import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf

internal class InntektsmeldingStatusDao(
    private val dataSource: DataSource
) {

    private fun InntektsmeldingStatus.status() = when (this) {
        is ManglerInntektsmelding -> "MANGLER_INNTEKTSMELDING"
        is HarInntektsmelding -> "HAR_INNTEKTSMELDING"
        is TrengerIkkeInntektsmelding -> "TRENGER_IKKE_INNTEKTSMELDING"
        is BehandlesUtenforSpleis -> "BEHANDLES_UTENFOR_SPLEIS"
        else -> throw IllegalStateException("Mangler status for ${javaClass.simpleName}")
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
}