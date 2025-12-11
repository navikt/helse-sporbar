package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.spedisjon.HentMeldingResponse
import com.github.navikt.tbd_libs.spedisjon.HentMeldingerResponse
import java.util.UUID

data class Dokument(val dokumentId: UUID, val type: Type) {
    enum class Type {
        Sykmelding, Søknad
    }
}

fun HentMeldingerResponse.tilSøknader() = this.meldinger
    .flatMap { it.tilDokument() }
    .filter { it.type == Dokument.Type.Søknad }
    .map { it.dokumentId }
    .toSet()
    .takeUnless(Set<UUID>::isEmpty)

fun HentMeldingerResponse.tilDokumenter() = this.meldinger.flatMap { it.tilDokument() }.distinctBy { it  }

private fun HentMeldingResponse.tilDokument(): List<Dokument> {
    val type = this.type.tilDokumenttypeOrNull()
    if (type != Dokument.Type.Søknad) return emptyList()

    val søknad = Dokument(dokumentId = this.eksternDokumentId, type = type)

    val sykmelding = runCatching {
        val sykmeldingId = objectMapper.readTree(this.jsonBody).path("sykmeldingId").asText().toUUID()
        Dokument(
            dokumentId = sykmeldingId,
            type = Dokument.Type.Sykmelding
        )
    }.getOrNull()
    return listOfNotNull(søknad, sykmelding)
}

private fun String.tilDokumenttypeOrNull() = when (this) {
    "sendt_søknad_arbeidsgiver",
    "sendt_søknad_nav",
    "sendt_søknad_frilans",
    "sendt_søknad_selvstendig",
    "sendt_søknad_arbeidsledig",
    "avbrutt_søknad",
    "avbrutt_arbeidsledig_søknad" -> Dokument.Type.Søknad

    else -> null
}
