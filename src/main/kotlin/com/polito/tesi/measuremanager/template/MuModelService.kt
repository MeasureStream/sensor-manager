package com.polito.tesi.measuremanager.template

import com.fasterxml.jackson.databind.ObjectMapper
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Crea gli slot di una MU leggendoli dal modello pubblicato nel registro.
 *
 * Prende il posto di `createMuByModel`, dove l'elenco dei sensori era scritto nel codice: un
 * modello nuovo richiedeva una modifica, una compilazione e un rilascio. Ora basta
 * pubblicare un documento `kind: mu`.
 *
 * Se il modello non e' nel registro la MU nasce **senza slot** invece di fallire: il
 * dispositivo resta censito, l'interfaccia lo mostra senza sensori, e alla pubblicazione del
 * modello gli slot vengono riempiti da soli (vedi [onTemplatePublished]).
 */
@Service
class MuModelService(
        private val registry: TemplateRegistry,
        private val objectMapper: ObjectMapper,
        private val mur: MeasurementUnitRepository,
) {
    private val logger = LoggerFactory.getLogger(MuModelService::class.java)

    /**
     * Il modello di MU corrispondente al codice dichiarato dal dispositivo.
     *
     * Con la notifica 0x11 il MAJOR arriva sul filo e si usa quello: e' la versione con cui
     * la MU e' stata programmata, e quindi l'unica con cui i suoi slot si leggono. Con la
     * vecchia 0x10, che non lo porta, resta il MAJOR piu' alto pubblicato — giusto finche'
     * ne esiste una versione sola.
     */
    fun resolveModel(model: Int, major: Int? = null): MuModelDocument? {
        val record =
                if (major != null) registry.resolve(TemplateKind.MU, model, major)
                else registry.resolveLatest(TemplateKind.MU, model)

        if (record == null) {
            logger.warn("Modello di MU 0x{} non nel registro: la MU nasce senza slot",
                    "%04X".format(model))
            return null
        }
        return objectMapper.convertValue(record.content, MuModelDocument::class.java)
    }

    /** Crea la MU e, se il modello e' risolto, i suoi slot. */
    fun createMeasurementUnit(
            extendedId: Long,
            model: Int,
            localId: Int,
            major: Int? = null,
    ): MeasurementUnit {
        val mu =
                MeasurementUnit().apply {
                    this.extendedId = extendedId
                    this.model = model
                    this.localId = localId
                    this.modelMajor = major
                    this.sensors = mutableListOf()
                }
        materialize(mu)
        return mu
    }

    /**
     * Riempie gli slot di una MU che ne e' priva. Restituisce true se ha creato qualcosa.
     * Non tocca una MU che ha gia' i sensori: quelli portano configurazione e storico.
     */
    fun materialize(mu: MeasurementUnit): Boolean {
        if (mu.sensors.isNotEmpty()) return false
        val document = resolveModel(mu.model, mu.modelMajor) ?: return false

        document.slots.sortedBy { it.index }.forEach { slot ->
            mu.sensors.add(buildSensor(mu, slot))
        }

        logger.info(
                "MU {} modello 0x{} MAJOR {}: creati {} slot dal template {}",
                mu.extendedId,
                "%04X".format(mu.model),
                mu.modelMajor ?: "piu' alto pubblicato",
                mu.sensors.size,
                document.templateVersion ?: "?",
        )
        return mu.sensors.isNotEmpty()
    }

    /**
     * Uno slot diventa un sensore. Il `modelName` arriva dal template di sensore risolto,
     * cosi' resta l'unica chiave usata dal resto del sistema; se il template non e' nel
     * registro si ripiega sull'etichetta dello slot, e il DTO dira' che non e' risolto.
     */
    private fun buildSensor(mu: MeasurementUnit, slot: MuSlot): Sensor {
        val sensorTemplate = registry.resolve(TemplateKind.SENSOR, slot.sensor.templateId, slot.sensor.major)
        if (sensorTemplate == null) {
            logger.warn(
                    "Slot {} del modello 0x{}: template di sensore {} MAJOR {} assente",
                    slot.index,
                    "%04X".format(mu.model),
                    slot.sensor.templateId,
                    slot.sensor.major,
            )
        }

        return Sensor(
                modelName = sensorTemplate?.modelName ?: slot.label ?: "unknown",
                measurementUnit = mu,
                sensorIndex = slot.index,
                channel = slot.channel,
                configurationMeasure = "average-std",
        )
    }

    /**
     * Riconciliazione: quando un modello di MU viene pubblicato, le MU censite che erano
     * rimaste senza slot li ricevono subito, senza riavviare e senza rifare il join.
     */
    @EventListener
    @Transactional
    fun onTemplatePublished(event: TemplatePublished) {
        if (event.kind != TemplateKind.MU) return

        // Solo le MU che aspettavano proprio questa versione: una MU che dichiara il MAJOR 2
        // non va riempita con gli slot del MAJOR 1 appena pubblicato.
        val major = event.version.substringBefore('.').toIntOrNull()
        val pending =
                mur.findAllByModel(event.keyId).filter {
                    it.sensors.isEmpty() && (it.modelMajor == null || it.modelMajor == major)
                }
        if (pending.isEmpty()) return

        pending.forEach { mu ->
            if (materialize(mu)) mur.save(mu)
        }
        logger.info(
                "Modello di MU {} {}: riconciliate {} MU rimaste senza slot",
                event.keyId,
                event.version,
                pending.size,
        )
    }
}
