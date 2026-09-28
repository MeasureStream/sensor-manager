package com.polito.tesi.measuremanager.template

/**
 * La `Stat bitmap` della 0x23: un bit per metrica, nell'ordine canonico del protocollo.
 *
 * Sta in un posto solo perche' e' la stessa mappa in due direzioni. Il server la manda alla
 * CU per dire quali statistiche calcolare, e la rilegge per sapere quali campi aspettarsi nel
 * report: se le due copie divergono, il decoder legge i byte di una metrica credendo che
 * siano quelli di un'altra, e il disallineamento non produce nessun errore.
 *
 * Finche' le metriche attive arrivano da `configurationMeasure`, la traduzione e' questa. Con
 * le soglie configurabili dall'interfaccia (§ 8 della roadmap) la bitmap arrivera' invece
 * dall'istantanea di configurazione, e questa funzione restera' solo per lo storico.
 */
object StatBitmap {

    /** Media e varianza: e' cio' che il server configura quando non gli si dice altro. */
    const val DEFAULT = 0b0000_0011

    fun of(configurationMeasure: String?): Int =
            when (configurationMeasure?.lowercase()) {
                "max-min" -> 0b0000_1100 // bit 2 (max) e 3 (min)
                "integral" -> 0b0001_0000 // bit 4
                "puntual", "punctual" -> 0b0000_0001 // bit 0, il valore corrente
                else -> DEFAULT
            }

    /** Il valore corrente non e' una statistica: viaggia da solo, fuori dall'ordine canonico. */
    fun isPunctual(configurationMeasure: String?): Boolean =
            configurationMeasure?.lowercase() in setOf("puntual", "punctual")
}
