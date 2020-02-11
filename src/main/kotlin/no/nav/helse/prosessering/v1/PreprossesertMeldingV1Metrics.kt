package no.nav.helse.prosessering.v1

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

private val ZONE_ID = ZoneId.of("Europe/Oslo")

private val barnetsAlderHistogram = Histogram.build()
    .buckets(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0)
    .name("barnets_alder_histogram")
    .help("Alderen på barnet det søkes for")
    .register()

private val idTypePaaBarnCounter = Counter.build()
    .name("id_type_paa_barn_counter")
    .help("Teller for hva slags ID-Type som er benyttet for å identifisere barnet")
    .labelNames("id_type")
    .register()

private val jaNeiCounter = Counter.build()
    .name("ja_nei_counter")
    .help("Teller for svar på ja/nei spørsmål i søknaden")
    .labelNames("spm", "svar")
    .register()

private val barnetsAlderIUkerCounter = Counter.build()
    .name("barnets_alder_i_uker")
    .help("Teller for barn under 1 år, hvor mange uker de er.")
    .labelNames("uker")
    .register()

internal fun PreprossesertMeldingV1.reportMetrics() {
    val barnetsFodselsdato = barn.fodseldato()
    if (barnetsFodselsdato != null) {
        val barnetsAlder = barnetsFodselsdato.aarSiden()
        barnetsAlderHistogram.observe(barnetsAlder)
        if (barnetsAlder.erUnderEttAar()) {
            barnetsAlderIUkerCounter.labels(barnetsFodselsdato.ukerSiden()).inc()
        }
    }
    idTypePaaBarnCounter.labels(barn.idType()).inc()
    jaNeiCounter.labels("har_bodd_i_utlandet_siste_12_mnd", medlemskap.harBoddIUtlandetSiste12Mnd.tilJaEllerNei()).inc()
    jaNeiCounter.labels("skal_bo_i_utlandet_neste_12_mnd", medlemskap.skalBoIUtlandetNeste12Mnd.tilJaEllerNei()).inc()
}

internal fun Double.erUnderEttAar() = 0.0 == this
private fun PreprossesertBarn.idType(): String {
    return when {
        norskIdentifikator != null -> "fødselsnummer"
        else -> "ingen_id"
    }
}
internal fun PreprossesertBarn.fodseldato() : LocalDate? {
    if (norskIdentifikator == null) return null
    return try {
        val dag = norskIdentifikator.substring(0,2).toInt()
        val maned = norskIdentifikator.substring(2,4).toInt()
        val ar = "20${norskIdentifikator.substring(4,6)}".toInt()
        LocalDate.of(ar, maned, dag)
    } catch (cause: Throwable) {
        null
    }
}
internal fun LocalDate.aarSiden() : Double {
    val alder= ChronoUnit.YEARS.between(this, LocalDate.now(ZONE_ID))
    if (alder in -18..-1) return 19.0
    return alder.absoluteValue.toDouble()
}
internal fun LocalDate.ukerSiden() = ChronoUnit.WEEKS.between(this, LocalDate.now(ZONE_ID)).absoluteValue.toString()
private fun Boolean.tilJaEllerNei(): String = if (this) "Ja" else "Nei"