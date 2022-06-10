package no.nav.helse.sporbar.inntektsmelding

import no.nav.helse.sporbar.vedtaksperiodeForkastet.VedtaksperiodeForkastetDao
import no.nav.helse.sporbar.vedtaksperiodeForkastet.VedtaksperiodeForkastetPakke
import org.intellij.lang.annotations.Language

internal class InntektsmeldingStatusMediator(
    private val inntektsmeldingDao: InntektsmeldingDao,
    private val vedtaksperiodeForkastetDao: VedtaksperiodeForkastetDao,
    private val inntektsmeldingStatusDao: InntektsmeldingStatusDao,
    private val producer: Producer? = null
) {
    internal fun lagre(inntektsmeldingStatus: InntektsmeldingStatus) {
        inntektsmeldingStatusDao.lagre(inntektsmeldingStatus)
    }

    internal fun lagre(inntektsmeldingPakke: InntektsmeldingPakke) {
        inntektsmeldingDao.lagre(inntektsmeldingPakke)
    }

    internal fun lagre(vedtaksperiodeForkastetPakke: VedtaksperiodeForkastetPakke) {
        vedtaksperiodeForkastetDao.lagre(vedtaksperiodeForkastetPakke)
    }

    internal fun sendInntektsmeldingStatuser(vedtaksperiodeForkastetPakke: VedtaksperiodeForkastetPakke) {
        producer?.send(vedtaksperiodeForkastetPakke.fødselsnummer, vedtaksperiodeForkastetPakke.somEksternDto())
    }

    @Language("JSON")
    private fun VedtaksperiodeForkastetPakke.somEksternDto() = """
        {
            "id": "$id",
            "status": "BEHANDLES_UTENFOR_SPLEIS",
            "sykmeldt": "$fødselsnummer",
            "arbeidsgiver": "$organisasjonsnummer",
            "vedtaksperiode": {
                "id": "$vedtaksperiodeId",
                "fom": "$fom",
                "tom": "$tom"
            },
            "tidsstempel": "$opprettet",
            "versjon": "1.0.0"
        }
    """
}