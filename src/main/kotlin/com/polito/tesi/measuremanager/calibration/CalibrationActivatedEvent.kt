package com.polito.tesi.measuremanager.calibration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.OffsetDateTime

/**
 * Un messaggio del topic compattato `calibration-activated` (specifica § 3 e appendice A).
 *
 * La chiave e' `EUID:indice`, con l'EUID in decimale come in `measurement_unit.extended_id`.
 * Un valore nullo con quella chiave (tombstone) revoca la taratura dell'esemplare.
 *
 * Il contratto lo scrive metrology-service: i campi nuovi arrivano sempre con un default,
 * cosi' un sensor-manager piu' vecchio li ignora invece di fallire.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CalibrationActivatedEvent(
        val schemaVersion: Int = 1,
        val muExtendedId: Long,
        val sensorIndex: Int,
        val calibratedItemId: Long? = null,
        val certificateId: Long,
        val certificateNumber: String? = null,
        val revision: Int = 1,
        val validFrom: OffsetDateTime,
        val validUntil: OffsetDateTime? = null,
        /** Stessa forma del blocco `calibration` di un template. */
        val function: Map<String, Any?>,
        val covariance: List<List<Double>>? = null,
        val calibratedRange: CalibratedRange? = null,
        val blockACrc: String? = null,
        val fingerprint: String? = null,
        /**
         * Quando e' stato misurato l'as-found da cui viene la funzione (fase 5): i campioni fra
         * questo istante e [validFrom] si riconvertono con la funzione nuova (GAP).
         */
        val asFoundAt: OffsetDateTime? = null,
        /**
         * L'inizio del periodo da correggere per deriva, presente solo se l'as-found era fuori
         * tolleranza: l'as-left precedente. Fra lui e [asFoundAt] la funzione si interpola (DRIFT).
         */
        val driftFrom: OffsetDateTime? = null,
) {
    fun key(): String = keyOf(muExtendedId, sensorIndex)

    companion object {
        const val TOPIC = "calibration-activated"

        fun keyOf(muExtendedId: Long, sensorIndex: Int) = "$muExtendedId:$sensorIndex"

        /** `EUID:indice` → la coppia, o null se la chiave non ha quella forma. */
        fun parseKey(key: String?): Pair<Long, Int>? {
            val parts = key?.split(':') ?: return null
            if (parts.size != 2) return null
            val euid = parts[0].trim().toLongOrNull() ?: return null
            val index = parts[1].trim().toIntOrNull() ?: return null
            return euid to index
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CalibratedRange(
        val min: Double? = null,
        val max: Double? = null,
        val dsi: String? = null,
)
