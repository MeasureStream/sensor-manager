package com.polito.tesi.measuremanager.uplink

/**
 * Le FPort applicative, in esadecimale come la documentazione (tabella 4.2).
 *
 * Nel protocollo v1.2 **la FPort e' il codice operativo**: nessun messaggio ripete il proprio
 * opcode dentro il payload. Scriverle in decimale nel codice, come si faceva prima, costringe
 * chi legge a convertire a mente ogni volta che confronta il codice con la documentazione: e'
 * il genere di attrito che produce errori silenziosi.
 */
object FPort {

    /* ------------------------------------------------ uplink CU -> server */

    /** Dummy: un byte che serve solo ad aprire una finestra di ricezione. */
    const val DUMMY = 0x02

    /** Stato della CU nel formato precedente alla v1.2: 5 byte, senza CFG_VER. */
    const val CU_STATUS_LEGACY = 0x0A

    /** Poll. Nove byte nella v1.2; sette nel formato precedente, che portava TEMPLATE_VER. */
    const val POLL = 0x0B

    /** Notifica delle MU connesse, deprecata: 4 byte per MU, senza versione del modello. */
    const val TOPOLOGY_LEGACY = 0x10

    /** Notifica delle MU connesse, versioned: 5 byte per MU, con il MAJOR del modello. */
    const val TOPOLOGY = 0x11

    /** Stato delle MU: STATUS_SEQ, MU_MAP e una parola di stato per MU. */
    const val MU_STATUS = 0x12

    /** Report dati: CFG_VER, CONTENT, FRAG e le metriche concatenate. */
    const val REPORT = 0x30

    /** Report nel formato precedente: solo CFG_VER e le metriche. */
    const val REPORT_LEGACY = 0x21

    /**
     * Secondo canale di misure introdotto nel codice e mai documentato: le metriche «extra»
     * viaggiavano su una porta propria. Il CONTENT della 0x30 fa la stessa cosa con tre bit.
     */
    const val REPORT_LEGACY_EXTRA = 0x31

    /** Risposta a un passthrough verso una MU o un sensore. */
    const val PASSTHROUGH_REPLY = 0x42

    /** Allarmi. */
    const val ALARMS = 0xA0

    /** Eventi e diagnostica. */
    const val EVENTS = 0xA2

    /* ------------------------------------------------ downlink server -> CU */

    /** Configurazione della CU: NumPacket, periodo di poll, Setting1, Command1. */
    const val CU_CONFIG = 0x0A

    /** Configurazione dei periodi: CU Trans. T e lo stream dei periodi di campionamento. */
    const val PERIODS = 0x21

    /** Programmazione completa di un'acquisizione. */
    const val SCHEDULE = 0x22

    /** Statistiche e soglie: Stat bitmap, Alarm enable e i record per slot. */
    const val STATS = 0x23

    /** Programmazione breve. */
    const val SHORT_SCHEDULE = 0x24

    /** Nome leggibile della porta, per i log e per la colonna `uplink_frame.fport`. */
    fun label(fport: Int): String =
            when (fport) {
                DUMMY -> "0x02 dummy"
                CU_STATUS_LEGACY -> "0x0A stato CU (formato precedente)"
                POLL -> "0x0B poll"
                TOPOLOGY_LEGACY -> "0x10 topologia (deprecata)"
                TOPOLOGY -> "0x11 topologia"
                MU_STATUS -> "0x12 stato MU"
                REPORT_LEGACY -> "0x21 report (formato precedente)"
                REPORT -> "0x30 report"
                REPORT_LEGACY_EXTRA -> "0x31 report extra (mai documentato)"
                PASSTHROUGH_REPLY -> "0x42 risposta passthrough"
                ALARMS -> "0xA0 allarmi"
                EVENTS -> "0xA2 eventi"
                else -> "0x%02X".format(fport)
            }
}
