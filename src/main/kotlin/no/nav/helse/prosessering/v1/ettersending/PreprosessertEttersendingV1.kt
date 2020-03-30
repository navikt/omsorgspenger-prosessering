package no.nav.helse.prosessering.v1.ettersending

import no.nav.helse.aktoer.AktørId
import no.nav.helse.prosessering.v1.PreprossesertSøker
import java.net.URI
import java.time.ZonedDateTime

data class PreprosessertEttersendingV1(
    val sprak: String?,
    val soknadId: String,
    val dokumentUrls: List<List<URI>>,
    val mottatt: ZonedDateTime,
    val søker: PreprossesertSøker,
    val harForstattRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val beskrivelse: String,
    val søknadstype: String,
    val titler: List<String>
    ) {
    internal constructor(
        melding: EttersendingV1,
        dokumentUrls: List<List<URI>>,
        sokerAktoerId: AktørId
    ) : this(
        sprak = melding.språk,
        soknadId = melding.søknadId,
        dokumentUrls = dokumentUrls,
        mottatt = melding.mottatt,
        søker = PreprossesertSøker(melding.søker, sokerAktoerId),
        beskrivelse = melding.beskrivelse,
        søknadstype = melding.søknadstype,
        harForstattRettigheterOgPlikter = melding.harForståttRettigheterOgPlikter,
        harBekreftetOpplysninger = melding.harBekreftetOpplysninger,
        titler = melding.titler
    )
}

