package com.polito.tesi.measuremanager.template

/**
 * Vista tipizzata di un documento `kind: mu`.
 *
 * Porta solo cio' che il server usa per creare gli slot; tutto il resto resta nel documento
 * e si legge dal registro quando serve.
 */
data class MuModelDocument(
        val templateId: Int = 0,
        val modelName: String? = null,
        val templateVersion: String? = null,
        val slots: List<MuSlot> = emptyList(),
        val properties: Map<String, Any?>? = null,
) {
    /** Numero massimo di slot dichiarato dal modello, quando c'e'. */
    val maxSlots: Int?
        get() = (properties?.get("maxSlots") as? Number)?.toInt()
}

/**
 * Uno slot: la posizione sul filo piu' il riferimento al template del sensore.
 * [channel] distingue le istanze dello stesso template (gli assi di un IMU); [label] e' la
 * grandezza, utile nei log e come ripiego quando il template non e' risolto.
 */
data class MuSlot(
        val index: Int = 0,
        val sensor: MuSensorRef = MuSensorRef(),
        val label: String? = null,
        val channel: String? = null,
)

/** Riferimento a un template di sensore: sempre `(templateId, MAJOR)`, mai la versione piena. */
data class MuSensorRef(
        val templateId: Int = 0,
        val major: Int = 0,
)
