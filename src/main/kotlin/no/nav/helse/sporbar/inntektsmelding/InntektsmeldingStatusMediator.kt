package no.nav.helse.sporbar.inntektsmelding

import java.time.Duration
import no.nav.helse.sporbar.objectMapper

internal class InntektsmeldingStatusMediator(
    private val inntektsmeldingStatusDao: InntektsmeldingStatusDao,
    private val producer: Producer? = null,
    private val statusTimeout: Duration = Duration.ofMinutes(1)
) {
    init {
        require(statusTimeout.seconds > 0) { "statusTimeout må settes til minst 1 sekund." }
    }

    internal fun lagre(inntektsmeldingStatus: InntektsmeldingStatus) {
        inntektsmeldingStatusDao.lagre(inntektsmeldingStatus)
    }

    internal fun publiser() {
        val statuser = inntektsmeldingStatusDao.hent(statusTimeout)
        statuser.forEach { status ->
            producer?.send(status.sykmeldt, objectMapper.writeValueAsString(status))
        }
        inntektsmeldingStatusDao.publisert(statuser)
    }
}