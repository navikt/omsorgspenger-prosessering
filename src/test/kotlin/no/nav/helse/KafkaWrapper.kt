package no.nav.helse

import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.helse.prosessering.Metadata
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.PreprossesertMeldingV1
import no.nav.helse.prosessering.v1.SøknadOverføreDager
import no.nav.helse.prosessering.v1.asynkron.Cleanup
import no.nav.helse.prosessering.v1.asynkron.Journalfort
import no.nav.helse.prosessering.v1.asynkron.TopicEntry
import no.nav.helse.prosessering.v1.asynkron.Topics.CLEANUP
import no.nav.helse.prosessering.v1.asynkron.Topics.CLEANUP_OVERFOREDAGER
import no.nav.helse.prosessering.v1.asynkron.Topics.JOURNALFORT
import no.nav.helse.prosessering.v1.asynkron.Topics.JOURNALFORT_OVERFOREDAGER
import no.nav.helse.prosessering.v1.asynkron.Topics.MOTTATT
import no.nav.helse.prosessering.v1.asynkron.Topics.MOTTATT_OVERFOREDAGER
import no.nav.helse.prosessering.v1.asynkron.Topics.PREPROSSESERT
import no.nav.helse.prosessering.v1.asynkron.Topics.PREPROSSESERT_OVERFOREDAGER
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals

private const val username = "srvkafkaclient"
private const val password = "kafkaclient"

object KafkaWrapper {
    fun bootstrap(): KafkaEnvironment {
        val kafkaEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = true,
            withSchemaRegistry = false,
            withSecurity = true,
            topicNames = listOf(
                MOTTATT.name,
                PREPROSSESERT.name,
                JOURNALFORT.name,
                CLEANUP.name,
                MOTTATT_OVERFOREDAGER.name,
                PREPROSSESERT_OVERFOREDAGER.name,
                JOURNALFORT_OVERFOREDAGER.name,
                CLEANUP_OVERFOREDAGER.name
            )
        )
        return kafkaEnvironment
    }
}

private fun KafkaEnvironment.testConsumerProperties(groupId: String): MutableMap<String, Any>? {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersURL)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(
            SaslConfigs.SASL_JAAS_CONFIG,
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
        )
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    }
}

private fun KafkaEnvironment.testProducerProperties(): MutableMap<String, Any>? {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersURL)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(
            SaslConfigs.SASL_JAAS_CONFIG,
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
        )
        put(ProducerConfig.CLIENT_ID_CONFIG, "OmsorgspengesoknadProsesseringTestProducer")
    }
}


fun KafkaEnvironment.journalføringsKonsumer(): KafkaConsumer<String, TopicEntry<Journalfort>> {
    val consumer = KafkaConsumer<String, TopicEntry<Journalfort>>(
        testConsumerProperties("K9FordelKonsumer"),
        StringDeserializer(),
        JOURNALFORT.serDes
    )
    consumer.subscribe(listOf(JOURNALFORT.name))
    return consumer
}

fun KafkaEnvironment.cleanupKonsumer(): KafkaConsumer<String, TopicEntry<Cleanup>> {
    val consumer = KafkaConsumer<String, TopicEntry<Cleanup>>(
        testConsumerProperties("OmsorgspengesøknadCleanupKonsumer"),
        StringDeserializer(),
        CLEANUP.serDes
    )
    consumer.subscribe(listOf(CLEANUP.name))
    return consumer
}

fun KafkaEnvironment.preprossesertKonsumer(): KafkaConsumer<String, TopicEntry<PreprossesertMeldingV1>> {
    val consumer = KafkaConsumer<String, TopicEntry<PreprossesertMeldingV1>>(
        testConsumerProperties("OmsorgspengesøknadPreprossesertKonsumer"),
        StringDeserializer(),
        PREPROSSESERT.serDes
    )
    consumer.subscribe(listOf(PREPROSSESERT.name))
    return consumer
}

fun KafkaEnvironment.meldingsProducer() = KafkaProducer<String, TopicEntry<MeldingV1>>(
    testProducerProperties(),
    MOTTATT.keySerializer,
    MOTTATT.serDes
)

fun KafkaEnvironment.meldingOverforeDagersProducer() = KafkaProducer<String, TopicEntry<SøknadOverføreDager>>(
    testProducerProperties(),
    MOTTATT_OVERFOREDAGER.keySerializer,
    MOTTATT_OVERFOREDAGER.serDes
)

fun KafkaConsumer<String, TopicEntry<Journalfort>>.hentJournalførtMelding(
    soknadId: String,
    maxWaitInSeconds: Long = 20
): TopicEntry<Journalfort> {
    val end = System.currentTimeMillis() + Duration.ofSeconds(maxWaitInSeconds).toMillis()
    while (System.currentTimeMillis() < end) {
        seekToBeginning(assignment())
        val entries = poll(Duration.ofSeconds(1))
            .records(JOURNALFORT.name)
            .filter { it.key() == soknadId }

        if (entries.isNotEmpty()) {
            assertEquals(1, entries.size)
            return entries.first().value()
        }
    }
    throw IllegalStateException("Fant ikke opprettet oppgave for søknad $soknadId etter $maxWaitInSeconds sekunder.")
}

fun KafkaConsumer<String, TopicEntry<PreprossesertMeldingV1>>.hentPreprossesertMelding(
    soknadId: String,
    maxWaitInSeconds: Long = 20
): TopicEntry<PreprossesertMeldingV1> {
    val end = System.currentTimeMillis() + Duration.ofSeconds(maxWaitInSeconds).toMillis()
    while (System.currentTimeMillis() < end) {
        seekToBeginning(assignment())
        val entries = poll(Duration.ofSeconds(1))
            .records(PREPROSSESERT.name)
            .filter { it.key() == soknadId }

        if (entries.isNotEmpty()) {
            assertEquals(1, entries.size)
            return entries.first().value()
        }
    }
    throw IllegalStateException("Fant ikke opprettet oppgave for søknad $soknadId etter $maxWaitInSeconds sekunder.")
}

fun KafkaConsumer<String, TopicEntry<Cleanup>>.hentCleanupMelding(
    soknadId: String,
    maxWaitInSeconds: Long = 20
): TopicEntry<Cleanup> {
    val end = System.currentTimeMillis() + Duration.ofSeconds(maxWaitInSeconds).toMillis()
    while (System.currentTimeMillis() < end) {
        seekToBeginning(assignment())
        val entries = poll(Duration.ofSeconds(1))
            .records(CLEANUP.name)
            .filter { it.key() == soknadId }

        if (entries.isNotEmpty()) {
            assertEquals(1, entries.size)
            return entries.first().value()
        }
    }
    throw IllegalStateException("Fant ikke opprettet oppgave for søknad $soknadId etter $maxWaitInSeconds sekunder.")
}

fun KafkaProducer<String, TopicEntry<MeldingV1>>.leggTilMottak(soknad: MeldingV1) {
    send(
        ProducerRecord(
            MOTTATT.name,
            soknad.søknadId,
            TopicEntry(
                metadata = Metadata(
                    version = 1,
                    correlationId = UUID.randomUUID().toString(),
                    requestId = UUID.randomUUID().toString()
                ),
                data = soknad
            )
        )
    ).get()
}

fun KafkaProducer<String, TopicEntry<SøknadOverføreDager>>.leggTilMottak(soknad: SøknadOverføreDager) {
    send(
        ProducerRecord(
            MOTTATT_OVERFOREDAGER.name,
            soknad.søknadId,
            TopicEntry(
                metadata = Metadata(
                    version = 1,
                    correlationId = UUID.randomUUID().toString(),
                    requestId = UUID.randomUUID().toString()
                ),
                data = soknad
            )
        )
    ).get()
}
fun KafkaEnvironment.username() = username
fun KafkaEnvironment.password() = password