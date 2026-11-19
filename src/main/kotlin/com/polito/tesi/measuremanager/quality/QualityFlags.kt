package com.polito.tesi.measuremanager.quality

/**
 * I flag di qualita' di un campione (specifica § 4.3), come bit di un intero.
 *
 * Si calcolano alla conversione e non si ricalcolano dopo, salvo nella riconversione: dicono
 * cosa si sapeva quando il valore e' nato. La posizione dei bit e' parte del dato salvato,
 * quindi un flag nuovo si aggiunge **in coda** e nessuno cambia posto.
 */
object QualityFlags {
    /** Il valore e' una grandezza fisica, non la lettura elettrica. */
    const val CONVERTED = 1 shl 0
    /** Convertito con la taratura dell'esemplare, non con la formula nominale del modello. */
    const val CAL_INSTANCE = 1 shl 1
    /** La taratura usata era scaduta: la funzione resta la migliore nota, ma va detto. */
    const val CAL_EXPIRED = 1 shl 2
    /** Il valore cade fuori dal campo tarato: la funzione e' estrapolata. */
    const val OUT_OF_CAL_RANGE = 1 shl 3
    /** Fra questo report e il precedente dello stesso sensore ne manca almeno uno. */
    const val GAP = 1 shl 4
    /** Letto con l'istantanea di una configurazione precedente (CFG_VER disallineato). */
    const val SNAPSHOT = 1 shl 5
    /** La CU dichiara l'orologio non sincronizzato: gli istanti sono meno affidabili. */
    const val TIME_UNSYNC = 1 shl 6
    /**
     * Il report non porta il byte di eta': la fine della finestra e' l'istante di ricezione,
     * che arriva dopo la raccolta di un tempo che non si conosce. E' il caso di ogni firmware
     * che non dichiara ancora l'eta' nel byte CONTENT.
     */
    const val NO_AGE = 1 shl 7
    /** La configurazione del dispositivo non corrisponde a quella del certificato (fase 6). */
    const val CONFIG_MISMATCH = 1 shl 8

    /** Nome e bit, nell'ordine: per i DTO, il CSV e l'interfaccia. */
    val ALL: List<Pair<String, Int>> =
            listOf(
                    "CONVERTED" to CONVERTED,
                    "CAL_INSTANCE" to CAL_INSTANCE,
                    "CAL_EXPIRED" to CAL_EXPIRED,
                    "OUT_OF_CAL_RANGE" to OUT_OF_CAL_RANGE,
                    "GAP" to GAP,
                    "SNAPSHOT" to SNAPSHOT,
                    "TIME_UNSYNC" to TIME_UNSYNC,
                    "NO_AGE" to NO_AGE,
                    "CONFIG_MISMATCH" to CONFIG_MISMATCH,
            )

    fun names(flags: Int?): List<String> =
            if (flags == null) emptyList() else ALL.filter { (_, bit) -> flags and bit != 0 }.map { it.first }
}
