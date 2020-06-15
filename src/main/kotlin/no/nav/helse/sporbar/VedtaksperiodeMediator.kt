package no.nav.helse.sporbar

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

private val log: Logger = LoggerFactory.getLogger("sporbar")

internal class VedtaksperiodeMediator(val vedtaksperiodeDao: VedtaksperiodeDao) {
    fun publiserEndring(vedtaksperiodeId: UUID) {
        log.info("skulle ha publisert et event på kø")
    }

}
