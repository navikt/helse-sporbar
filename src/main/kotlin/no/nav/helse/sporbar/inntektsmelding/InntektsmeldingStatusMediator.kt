package no.nav.helse.sporbar.inntektsmelding

internal class InntektsmeldingStatusMediator(
    private val inntektsmeldingStatusDao: InntektsmeldingStatusDao,
    private val producer: Producer? = null
) {
    internal fun lagre(inntektsmeldingStatus: InntektsmeldingStatus) {
        inntektsmeldingStatusDao.lagre(inntektsmeldingStatus)
    }
}