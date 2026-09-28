package com.polito.tesi.measuremanager.dtos

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Un report pronto da leggere: l'header gia' separato dai dati e i frammenti gia' ricomposti.
 *
 * Non arriva piu' da Kafka: lo compone il router degli uplink a partire dai byte grezzi, sia
 * per il report 0x30 sia per la vecchia 0x21. Cosi' il resto del server vede una forma sola,
 * e la differenza fra i due formati vive dove sta la differenza, cioe' nell'header.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CuMeasuresUpdate(
        val devEui: Long,
        val deviceId: String,
        val configVersion: Int,
        /** I soli byte delle metriche, in base64: l'header non c'e' piu'. */
        val rawPayload: String,
        val timestamp: String?,
        /**
         * Il byte CONTENT: quali classi di metriche porta questo report. Null per la 0x21,
         * che non lo prevede, e vuol dire «tutte quelle configurate».
         */
        val content: Int? = null,
        /** Quanti frammenti sono serviti a comporlo. */
        val fragments: Int = 1,
        /** La porta da cui e' arrivato: 0x30, oppure 0x21 nel formato precedente. */
        val fport: Int = 0x30,
)
