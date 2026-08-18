package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.spedisjon.HentMeldingResponse
import com.github.navikt.tbd_libs.spedisjon.HentMeldingerResponse
import java.util.*

data class Dokument(
    val dokumentId: UUID,
    val type: Type,
) {
    enum class Type {
        Sykmelding,
        Søknad,
        Inntektsmelding,
    }
}

fun HentMeldingerResponse.tilSøknader() =
    this.meldinger
        .flatMap { it.tilSøknadsdokument() }
        .filter { it.type == Dokument.Type.Søknad }
        .map { it.dokumentId }
        .toSet()
        .takeUnless(Set<UUID>::isEmpty)

fun HentMeldingerResponse.tilSøknadsdokumenter() = this.meldinger.flatMap { it.tilSøknadsdokument() }.distinctBy { it }

fun HentMeldingerResponse.tilAlleDokumenter(): List<Dokument> =
    this.meldinger.flatMap {
        val type = it.type.tilDokumenttypeOrNull()
        val eksternDokumentId = it.eksternDokumentId
        when (type) {
            Dokument.Type.Søknad -> {
                val sykmelding =
                    runCatching {
                        val sykmeldingId =
                            objectMapper
                                .readTree(it.jsonBody)
                                .path("sykmeldingId")
                                .asText()
                                .toUUID()
                        Dokument(
                            dokumentId = sykmeldingId,
                            type = Dokument.Type.Sykmelding,
                        )
                    }.getOrNull()
                val søknad =
                    Dokument(
                        dokumentId = it.eksternDokumentId,
                        type = type,
                    )
                listOfNotNull(sykmelding, søknad)
            }
            Dokument.Type.Inntektsmelding -> listOf(Dokument(eksternDokumentId, type))

            Dokument.Type.Sykmelding,
            null,
            -> emptyList()
        }
    }.distinctBy { it.dokumentId }

private fun HentMeldingResponse.tilSøknadsdokument(): List<Dokument> {
    val type = this.type.tilDokumenttypeOrNull()
    if (type != Dokument.Type.Søknad) return emptyList()

    val søknad = Dokument(dokumentId = this.eksternDokumentId, type = type)

    val sykmelding =
        runCatching {
            val sykmeldingId =
                objectMapper
                    .readTree(this.jsonBody)
                    .path("sykmeldingId")
                    .asText()
                    .toUUID()
            Dokument(
                dokumentId = sykmeldingId,
                type = Dokument.Type.Sykmelding,
            )
        }.getOrNull()
    return listOfNotNull(søknad, sykmelding)
}

private fun String.tilDokumenttypeOrNull() =
    when (this) {
        "avbrutt_annet_søknad",
        "avbrutt_arbeidsledig_søknad",
        "avbrutt_fisker_søknad",
        "avbrutt_frilanser_søknad",
        "avbrutt_jordbruker_søknad",
        "avbrutt_selvstendig_søknad",
        "avbrutt_søknad",

        "sendt_søknad_arbeidsgiver",
        "sendt_søknad_arbeidsledig",
        "sendt_søknad_frilans",
        "sendt_søknad_nav",
        "sendt_søknad_selvstendig",
        -> Dokument.Type.Søknad

        "ny_søknad",
        "ny_søknad_arbeidsledig",
        "ny_søknad_frilans",
        "ny_søknad_selvstendig",
        -> Dokument.Type.Sykmelding

        "selvbestemte_arbeidsgiveropplysninger",
        "arbeidsgiveropplysninger",
        "inntektsmelding",
        "korrigerte_arbeidsgiveropplysninger",
        -> Dokument.Type.Inntektsmelding

        else -> null
    }
