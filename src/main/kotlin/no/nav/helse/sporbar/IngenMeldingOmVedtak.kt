package no.nav.helse.sporbar

import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("sporbar")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class IngenMeldingOmVedtak(private val spesialsakDao: SpesialsakDao) {
    fun ignorerMeldingOmVedtak(vedtaksperiodeId: String, vararg oppdragDto: UtbetalingUtbetalt.OppdragDto?) =
        spesialsakDao.spesialsak(UUID.fromString(vedtaksperiodeId)) && oppdragDto.mapNotNull { it }.all { it.nettoBeløp == 0 }
}