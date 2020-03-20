package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.ManagedKafkaStreams
import no.nav.helse.kafka.ManagedStreamHealthy
import no.nav.helse.kafka.ManagedStreamReady
import no.nav.helse.prosessering.v1.PreprosseseringV1Service
import no.nav.helse.prosessering.v1.SøknadOverføreDagerV1
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory

internal class PreprosseseringStreamOverforeDager(
    preprosseseringV1Service: PreprosseseringV1Service, // TODO: Du må lage en ny service også
    kafkaConfig: KafkaConfig
) {
    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(preprosseseringV1Service),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {

        private const val NAME = "PreprosesseringV1OverforeDager"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(preprosseseringV1Service: PreprosseseringV1Service): Topology {
            val builder = StreamsBuilder()
            val fromMottatt = Topics.MOTTATT_OVERFOREDAGER
            val tilPreprossesert = Topics.PREPROSSESERT_OVERFOREDAGER

            builder
                .stream<String, TopicEntry<SøknadOverføreDagerV1>>(
                    fromMottatt.name,
                    Consumed.with(fromMottatt.keySerde, fromMottatt.valueSerde)
                )
                .filter { _, entry -> 1 == entry.metadata.version }
                .mapValues { soknadId, entry ->
                    process(NAME, soknadId, entry) {
                        logger.info("Preprosesserer søknad for overføring av dager.")
                        val preprossesertMelding = preprosseseringV1Service.preprosseserOverforeDager(
                            melding = entry.data,
                            metadata = entry.metadata
                        )
                        logger.info("Preprossesering ferdig.")
                        preprossesertMelding
                    }
                }
                .to(tilPreprossesert.name, Produced.with(tilPreprossesert.keySerde, tilPreprossesert.valueSerde))
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
