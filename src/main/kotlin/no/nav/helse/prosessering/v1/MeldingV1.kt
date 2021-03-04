package no.nav.helse.prosessering.v1

import com.fasterxml.jackson.annotation.JsonFormat
import java.net.URI
import java.time.LocalDate
import java.time.ZonedDateTime

data class MeldingV1(
    val nyVersjon: Boolean = false,
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String? = "nb",
    val kroniskEllerFunksjonshemming: Boolean = false,
    val barn: Barn,
    val søker: Søker,
    val relasjonTilBarnet: SøkerBarnRelasjon? = null,
    val sammeAdresse: Boolean = false,
    var legeerklæring: List<URI> = listOf(),
    var samværsavtale: List<URI> = listOf(),
    val harBekreftetOpplysninger: Boolean,
    val harForståttRettigheterOgPlikter: Boolean
)

data class Søker(
    val fødselsnummer: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    @JsonFormat(pattern = "yyyy-MM-dd") val fødselsdato: LocalDate?,
    val aktørId: String
) {
    override fun toString(): String {
        return "Soker(fornavn='$fornavn', mellomnavn=$mellomnavn, etternavn='$etternavn', fødselsdato=$fødselsdato, aktørId='$aktørId')"
    }
}

data class Barn(
    val navn: String?,
    val norskIdentifikator: String?,
    @JsonFormat(pattern = "yyyy-MM-dd") val fødselsdato: LocalDate? = null,
    val aktørId: String?
) {
    override fun toString(): String {
        return "Barn(navn=$navn, aktørId=$aktørId)"
    }
}

enum class SøkerBarnRelasjon(val utskriftsvennlig: String) {
    MOR("Mor"),
    FAR("Far"),
    ADOPTIVFORELDER("Adoptivforelder"),
    FOSTERFORELDER("Fosterforelder")
}