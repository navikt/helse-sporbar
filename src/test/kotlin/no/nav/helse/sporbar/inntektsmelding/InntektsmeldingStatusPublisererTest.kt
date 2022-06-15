package no.nav.helse.sporbar.inntektsmelding

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.Duration.ZERO
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class InntektsmeldingStatusPublisererTest {

    private val testRapid = TestRapid()
    private val inntektsmeldingStatusMediator = mockk<InntektsmeldingStatusMediator>(relaxed = true)
    private val publiseringsintervall = Duration.ofMillis(50)

    init {
        InntektsmeldingStatusPubliserer(testRapid, inntektsmeldingStatusMediator, publiseringsintervall)
    }

    @BeforeEach
    fun reset() {
        clearMocks(inntektsmeldingStatusMediator)
        testRapid.reset()
    }

    @Test
    fun `publiserer meldinger i gitt publiseringsintervall`() = runBlocking {
        verify(exactly = 0) { inntektsmeldingStatusMediator.publiser(any()) }
        testRapid.sendHvaSomHelst()
        verify(exactly = 1) { inntektsmeldingStatusMediator.publiser(any()) }
        repeat(50) { testRapid.sendHvaSomHelst() }
        verify(exactly = 1) { inntektsmeldingStatusMediator.publiser(any()) }
        delay(publiseringsintervall.toMillis() + 1)
        testRapid.sendHvaSomHelst()
        verify(exactly = 2) { inntektsmeldingStatusMediator.publiser(any()) }
        repeat(50) { testRapid.sendHvaSomHelst() }
        verify(exactly = 2) { inntektsmeldingStatusMediator.publiser(any()) }
    }

    @Test
    fun `ugyldig publiseringsintervall`() {
        assertThrows<IllegalArgumentException> { InntektsmeldingStatusPubliserer(testRapid, inntektsmeldingStatusMediator, ZERO.minusMillis(1)) }
    }

    @Test
    fun `ugyldig statustimeout`() {
        assertThrows<IllegalArgumentException> { InntektsmeldingStatusPubliserer(testRapid, inntektsmeldingStatusMediator, publiseringsintervall, Duration.ofMillis(999)) }
    }

    private fun TestRapid.sendHvaSomHelst() = sendTestMessage("""{"event":"${UUID.randomUUID()}"}""")
}