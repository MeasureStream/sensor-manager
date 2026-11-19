package com.polito.tesi.measuremanager.calibration

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaConsumerFactory

/**
 * Kafka per la metrologia: il consumer del topic compattato e la dichiarazione dei topic.
 *
 * Il consumer non usa la fabbrica predefinita, che deserializza in JSON con un tipo fisso e
 * non sa cosa fare di un valore nullo: qui il tombstone e' un messaggio con un significato
 * (revoca), quindi chiave e valore arrivano come testo e il JSON lo legge il listener.
 */
@Configuration
class CalibrationKafkaConfig {

    @Bean
    fun calibrationActivatedFactory(
            properties: KafkaProperties,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val config = HashMap(properties.buildConsumerProperties(null))
        config[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        config[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        config[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        config.remove("spring.deserializer.key.delegate.class")
        config.remove("spring.deserializer.value.delegate.class")

        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(config)
        }
    }

    /**
     * Compattato: Kafka tiene l'ultimo messaggio per chiave, cioe' la taratura in vigore per
     * ogni esemplare, ed e' cio' che serve per ricostruire lo stato rileggendo da capo. Lo
     * dichiara anche metrology-service, che ne e' l'autore: chi parte per primo lo crea con
     * la politica giusta invece di lasciarlo creare al broker con quella predefinita.
     */
    @Bean
    fun calibrationActivatedTopic() =
            TopicBuilder.name(CalibrationActivatedEvent.TOPIC).partitions(1).replicas(1).compact().build()

    @Bean
    fun sampleConvertedTopic() = TopicBuilder.name(SAMPLE_CONVERTED_TOPIC).partitions(1).replicas(1).build()

    companion object {
        const val SAMPLE_CONVERTED_TOPIC = "sample-converted"
    }
}
