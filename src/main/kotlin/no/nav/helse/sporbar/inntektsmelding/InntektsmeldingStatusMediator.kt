package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sporbar.Meldingstype
import no.nav.helse.sporbar.inntektsmelding.Producer.Melding
import no.nav.helse.sporbar.objectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class InntektsmeldingStatusMediator(
    private val inntektsmeldingStatusDao: InntektsmeldingStatusDao,
    private val producer: Producer
) {

    internal fun lagre(inntektsmeldingStatus: InntektsmeldingStatus) {
        inntektsmeldingStatusDao.lagre(inntektsmeldingStatus)
    }

    internal fun publiser(statustimeout: Duration) {
        val statuser = inntektsmeldingStatusDao.hent(statustimeout)
        sikkerLogg.info("Publiserer ${statuser.size} inntektsmeldingstatuser.")
        statuser.forEach { status ->
            val json = objectMapper.valueToTree<JsonNode>(status)
            producer.send(Melding(
                topic = "tbd.inntektsmeldingstatus",
                meldingstype = Meldingstype.Inntektsmeldingstatus,
                key = status.sykmeldt,
                json = json
            ))
            sikkerLogg.info("Publiserer inntektsmeldingstatus:\n\t$json", keyValue("vedtaksperiodeId", status.vedtaksperiode.id))
        }
        inntektsmeldingStatusDao.publisert(statuser)
    }

    private companion object {
        private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
    }
}