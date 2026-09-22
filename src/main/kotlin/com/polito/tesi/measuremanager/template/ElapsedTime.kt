package com.polito.tesi.measuremanager.template

/**
 * La codifica di Δt su un byte (§ 4.13 della documentazione v1.2).
 *
 * La risoluzione è fine dove serve e grossolana dove non serve: 138 valori su 256 stanno
 * nella fascia fra un'ora e un giorno, dove si concentrano gli eventi che hanno atteso in
 * coda il periodo di poll.
 *
 * Il passo della fascia **è un'incertezza di quantizzazione**, non un dettaglio: un evento a
 * 71 non è «due ore fa», è «due ore fa più o meno cinque minuti». Per questo la conversione
 * restituisce anche il passo, e il server lo conserva accanto all'istante.
 */
object ElapsedTime {

    /** Secondi trascorsi e incertezza della fascia; null se l'istante non è rappresentabile. */
    data class Elapsed(val seconds: Long, val uncertaintySeconds: Int)

    fun decode(raw: Int): Elapsed? =
            when (raw) {
                // Meno di dieci secondi: la fascia stessa è l'incertezza.
                0 -> Elapsed(0, 10)
                in 1..6 -> Elapsed(raw * 10L, 10)
                in 7..65 -> Elapsed((raw - 5) * 60L, 60)
                in 66..203 -> Elapsed((raw - 59) * 10L * 60L, 10 * 60)
                in 204..227 -> Elapsed((raw - 179) * 3600L, 3600)
                in 228..254 -> Elapsed((raw - 225) * 86400L, 86400)
                // 255: più vecchio del massimo rappresentabile, oppure istante sconosciuto.
                else -> null
            }
}
