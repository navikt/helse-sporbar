package no.nav.helse.sporbar

import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

object IngenMeldingOmVedtak {

    private val vedtaksperioder = IngenMeldingOmVedtak::class.java
        .getResource("/blocklist.txt")
        ?.readText()
        ?.lines()
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()

    init {
        "Har ${vedtaksperioder.size} vedtaksperioder i blocklist".also {
            log.info(it)
            sikkerLog.info(it)
        }
    }

    fun ignorerMeldingOmVedtak(vedtaksperiodeId: String, vararg oppdragDto: UtbetalingUtbetalt.OppdragDto?) =
        vedtaksperiodeId in vedtaksperioder && oppdragDto.mapNotNull { it }.all { it.nettoBeløp == 0 }
}