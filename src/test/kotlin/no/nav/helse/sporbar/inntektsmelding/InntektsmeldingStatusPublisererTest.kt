package no.nav.helse.sporbar.inntektsmelding

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class InntektsmeldingStatusPublisererTest {

    private val testRapid = TestRapid()
    private val inntektsmeldingStatusMediator = mockk<InntektsmeldingStatusMediator>(relaxed = true)

    init {
        InntektsmeldingStatusPubliserer(testRapid, inntektsmeldingStatusMediator)
    }

    @BeforeEach
    fun reset() {
        clearMocks(inntektsmeldingStatusMediator)
        testRapid.reset()
    }

    @Test
    fun `publiserer meldinger i gitt publiseringsintervall`() {
        verify(exactly = 0) { inntektsmeldingStatusMediator.publiser(any()) }
        testRapid.publiserInntektsmeldingstatus()
        verify(exactly = 1) { inntektsmeldingStatusMediator.publiser(any()) }
    }

    @Test
    fun `publiserer meldinger i gitt publiseringsintervall - minutt`() {
        verify(exactly = 0) { inntektsmeldingStatusMediator.publiser(any()) }
        testRapid.publiserMinuttevent()
        verify(exactly = 1) { inntektsmeldingStatusMediator.publiser(any()) }
    }

    @Test
    fun `ugyldig statustimeout`() {
        assertThrows<IllegalArgumentException> { InntektsmeldingStatusPubliserer(
            testRapid,
            inntektsmeldingStatusMediator,
            Duration.ofMillis(999)
        ) }
    }

    private fun TestRapid.publiserInntektsmeldingstatus() = sendTestMessage("""{"@event_name":"publiser_im_status"}""")
    private fun TestRapid.publiserMinuttevent() = sendTestMessage("""{"@event_name":"minutt"}""")
}