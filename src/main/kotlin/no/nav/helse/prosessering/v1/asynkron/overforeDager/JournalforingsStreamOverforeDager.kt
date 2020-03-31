package no.nav.helse.prosessering.v1.asynkron.overforeDager

import no.nav.helse.CorrelationId
import no.nav.helse.aktoer.AktørId
import no.nav.helse.joark.JoarkGateway
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.ManagedKafkaStreams
import no.nav.helse.kafka.ManagedStreamHealthy
import no.nav.helse.kafka.ManagedStreamReady
import no.nav.helse.prosessering.v1.overforeDager.PreprossesertOverforeDagerV1
import no.nav.helse.prosessering.v1.PreprossesertSøker
import no.nav.helse.prosessering.v1.asynkron.*
import no.nav.helse.prosessering.v1.asynkron.Topic
import no.nav.helse.prosessering.v1.asynkron.Topics
import no.nav.helse.prosessering.v1.asynkron.process
import no.nav.helse.prosessering.v1.overforeDager.Fosterbarn
import no.nav.k9.søknad.felles.Barn
import no.nav.k9.søknad.felles.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.Søker
import no.nav.k9.søknad.felles.SøknadId
import no.nav.k9.søknad.omsorgspenger.overføring.Mottaker
import no.nav.k9.søknad.omsorgspenger.overføring.OmsorgspengerOverføringSøknad
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory

internal class JournalforingsStreamOverforeDager(
    joarkGateway: JoarkGateway,
    kafkaConfig: KafkaConfig
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(joarkGateway),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "JournalforingV1OverforeDager"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(joarkGateway: JoarkGateway): Topology {
            val builder = StreamsBuilder()
            val fraPreprossesertV1: Topic<TopicEntry<PreprossesertOverforeDagerV1>> = Topics.PREPROSSESERT_OVERFOREDAGER
            val tilCleanup: Topic<TopicEntry<CleanupOverforeDager>> = Topics.CLEANUP_OVERFOREDAGER

            val mapValues = builder
                .stream<String, TopicEntry<PreprossesertOverforeDagerV1>>(
                    fraPreprossesertV1.name,
                    Consumed.with(fraPreprossesertV1.keySerde, fraPreprossesertV1.valueSerde)
                )
                .filter { _, entry -> 1 == entry.metadata.version }
                .mapValues { soknadId, entry ->
                    process(NAME, soknadId, entry) {

                        val dokumenter = entry.data.dokumentUrls
                        logger.info("Journalfører overføre dager dokumenter: {}", dokumenter)
                        val journaPostId = joarkGateway.journalførOverforeDager(
                            mottatt = entry.data.mottatt,
                            aktørId = AktørId(entry.data.søker.aktørId),
                            norskIdent = entry.data.søker.fødselsnummer,
                            correlationId = CorrelationId(entry.metadata.correlationId),
                            dokumenter = dokumenter
                        )
                        logger.info("Dokumenter til overføre dager journalført med ID = ${journaPostId.journalpostId}.")

                        val journalfort = JournalfortOverforeDager(
                            journalpostId = journaPostId.journalpostId,
                                søknad = entry.data.tilK9OmsorgspengerOverføringSøknad()
                            )

                        CleanupOverforeDager(
                            metadata = entry.metadata,
                            meldingV1 = entry.data,
                            journalførtMelding = journalfort
                        )
                    }
                }
            mapValues
                .to(tilCleanup.name, Produced.with(tilCleanup.keySerde, tilCleanup.valueSerde))
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}

private fun PreprossesertOverforeDagerV1.tilK9OmsorgspengerOverføringSøknad(): OmsorgspengerOverføringSøknad {
    val builder = OmsorgspengerOverføringSøknad.builder()
        .søknadId(SøknadId.of(soknadId))
        .mottattDato(mottatt)
        .søker(søker.tilK9Søker())
        .mottaker(fnrMottaker.tilK9Mottaker())

    fosterbarn?.let { builder.barn(it.tilK9Barn()) }

    return builder.build()
}

private fun List<Fosterbarn>.tilK9Barn(): List<Barn> {
    return map {
        Barn.builder()
            .norskIdentitetsnummer(NorskIdentitetsnummer.of(it.fødselsnummer))
            .build()
    }
}

private fun PreprossesertSøker.tilK9Søker() = Søker.builder()
    .norskIdentitetsnummer(NorskIdentitetsnummer.of(fødselsnummer))
    .build()

private fun String.tilK9Mottaker() = Mottaker.builder()
    .norskIdentitetsnummer(NorskIdentitetsnummer.of(this))
    .build()