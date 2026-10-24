package com.polito.tesi.measuremanager.template

/**
 * La `Stat bitmap` della 0x23: un bit per metrica, nell'ordine canonico del protocollo.
 *
 * Sta in un posto solo perche' e' la stessa mappa in due direzioni. Il server la manda alla
 * CU per dire quali statistiche calcolare, e la rilegge per sapere quali campi aspettarsi nel
 * report: se le due copie divergono, il decoder legge i byte di una metrica credendo che
 * siano quelli di un'altra, e il disallineamento non produce nessun errore.
 *
 * I bit seguono l'ordine canonico del dizionario di protocollo: 0 media, 1 varianza, 2 max,
 * 3 min, 4 integrale, 5 tot_h, 6 tot_l, 7 mediana, 8 p_high, 9 p_low, 10 zcr.
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
                "median" -> 0b1000_0000 // bit 7
                "puntual", "punctual" -> 0b0000_0001 // bit 0, il valore corrente
                else -> DEFAULT
            }

    /** Il valore corrente non e' una statistica: viaggia da solo, fuori dall'ordine canonico. */
    fun isPunctual(configurationMeasure: String?): Boolean =
            configurationMeasure?.lowercase() in setOf("puntual", "punctual")

    /**
     * Traduce l'identificatore della misura usato dall'interfaccia nel nome canonico che si
     * conserva in `Sensor.configurationMeasure`.
     *
     * La traduzione vive qui e non nel pianificatore perche' e' l'altra meta' di [of]: chi
     * aggiunge una misura nuova trova le due direzioni a tre righe di distanza e non puo'
     * aggiornarne una sola.
     *
     * Restituisce `null` per le misure che l'interfaccia sa proporre ma il protocollo non sa
     * ancora trasportare. Oggi e' il caso del percentile: i bit `p_high` e `p_low` esistono
     * nell'ordine canonico, ma il record della 0x23 non ha nessun campo in cui scrivere
     * **quale** percentile, e spedirne uno scelto dal server sarebbe peggio che non spedirlo.
     */
    fun canonicalMeasure(measureId: String?): String? =
            when (measureId?.lowercase()) {
                "avg" -> "avg-std"
                "mm" -> "max-min"
                "int" -> "integral"
                "med" -> "median"
                "pt" -> "puntual"
                "pct" -> null
                else -> null
            }
}
