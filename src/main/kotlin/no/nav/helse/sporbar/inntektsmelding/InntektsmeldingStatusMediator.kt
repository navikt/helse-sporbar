package no.nav.helse.sporbar.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import java.lang.System.currentTimeMillis
import java.time.Duration
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sporbar.Meldingstype
import no.nav.helse.sporbar.inntektsmelding.Producer.Melding
import no.nav.helse.sporbar.objectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

internal class InntektsmeldingStatusMediator(
    private val inntektsmeldingStatusDao: InntektsmeldingStatusDao,
    private val producer: Producer
) {

    internal fun lagre(inntektsmeldingStatus: InntektsmeldingStatus) {
        inntektsmeldingStatusDao.lagre(inntektsmeldingStatus)
    }

    private fun hent(statustimeout: Duration): Pair<Long, List<InntektsmeldingStatusForEksternDto>>? {
        val start = currentTimeMillis()
        val statuser = inntektsmeldingStatusDao.hent(statustimeout).takeUnless { it.isEmpty() } ?: return null
        return currentTimeMillis() - start to statuser
    }

    internal fun publiser(statustimeout: Duration) {
        val (databasetidsbruk, statuser) = hent(statustimeout) ?: return
        logg.info("Starter publisering av ${statuser.size} inntektsmeldingstatuser. Se sikkerlogg for detaljer.")
        val tidsbruk = measureTimeMillis {
            statuser.forEach { status ->
                val json = objectMapper.valueToTree<JsonNode>(status)
                producer.send(Melding(
                    topic = "tbd.inntektsmeldingstatus",
                    meldingstype = Meldingstype.Inntektsmeldingstatus,
                    key = status.sykmeldt,
                    json = json
                ))
                sikkerLogg.info("Publiserer inntektsmeldingstatus for {} fra ${ISO_OFFSET_DATE_TIME.format(status.tidspunkt)}:\n\t$json", keyValue("vedtaksperiodeId", status.vedtaksperiode.id))
            }
            inntektsmeldingStatusDao.publisert(statuser)
        }
        logg.info("Ferdigstilt publisering av ${statuser.size} inntektsmeldingstatuser på ${tidsbruk + databasetidsbruk} millisekunder.")
    }

    private companion object {
        private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
        private val logg: Logger = LoggerFactory.getLogger("inntektsmeldingstatus")
    }
}