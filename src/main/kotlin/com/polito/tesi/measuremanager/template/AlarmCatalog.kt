package com.polito.tesi.measuremanager.template

/**
 * I testi delle condizioni di allarme e dei codici evento definiti da MeasureStream.
 *
 * Lo spazio degli allarmi e' **chiuso a cinque bit** (§ 4.9): ogni condizione nuova di
 * piattaforma e' un evento, non un bit di allarme. I bit 5 e 6 appartengono al costruttore e
 * si risolvono dal template del sensore; dalla terza condizione in poi stanno nella catena
 * di estensione.
 *
 * Questi testi non passano da `translations.ts`: nascono dal protocollo e dai template, e
 * devono restare leggibili anche in un log o in un certificato, dove non c'e' un'interfaccia
 * che traduca.
 */
object AlarmCatalog {

    /** Bit 0-4 della parola di allarme: lo spazio MeasureStream. */
    val CONDITIONS: Map<Int, String> =
            mapOf(
                    0 to "Soglia alta superata",
                    1 to "Soglia bassa superata",
                    2 to "RoC alto superato",
                    3 to "RoC basso superato",
                    4 to "Lettura fuori dai limiti fisici del sensore",
            )

    /** Bit 7 della parola di allarme: segue un byte di estensione, in coda al messaggio. */
    const val CONTINUATION_BIT = 7

    /**
     * Codici evento 0x00-0x7F, assegnati da MeasureStream (§ 4.11).
     * Da 0x80 in su lo spazio e' dei costruttori e si risolve dal template del modello di MU.
     */
    val EVENTS: Map<Int, EventSpec> =
            mapOf(
                    0x01 to EventSpec("Overflow dell'event log: eventi scartati", "eventi persi"),
                    0x40 to EventSpec("Comunicazione col sensore fallita", "fallimenti consecutivi"),
                    0x41 to EventSpec("Inizializzazione del sensore non completata al boot", null),
                    0x42 to EventSpec("Pacchetto di misure sovrascritto prima dell'invio", null),
                    0x43 to EventSpec("Nessun campione entro due periodi di campionamento", null),
            )

    /** Descrizione di un codice evento e significato del suo parametro. */
    data class EventSpec(val description: String, val paramMeaning: String?)

    /** A quale fascia appartiene un codice: dice anche chi lo ha assegnato. */
    fun ownerOf(code: Int): String =
            when (code) {
                in 0x00..0x3F -> "CU"
                in 0x40..0x7F -> "sensore"
                else -> "costruttore"
            }
}
