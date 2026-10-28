package com.polito.tesi.measuremanager.dtos

/**
 * Il contratto della schermata «Configurazione sensori».
 *
 * Il frontend non produce byte: produce comandi **semantici**, gia' raggruppati per valore
 * identico e con il destinatario espresso nella forma piu' economica (`SensorSelectorDTO`).
 * Al server resta la parte che sul filo conta davvero: espandere i gruppi in record per
 * sensore, comporre i payload e tenere i contatori.
 *
 * Il raggruppamento non si perde: resta nella richiesta come documentazione di cosa l'utente
 * ha chiesto, e il giorno in cui il protocollo imparera' i selettori il server potra'
 * spedirli cosi' come sono, senza che l'interfaccia cambi di una riga.
 *
 * **Campi derivati ignorati di proposito.** Il frontend manda anche `opcode`, `measureCode`,
 * `fieldMask` e `estimate`: sono calcolati da lui per disegnare la stima a schermo, e il
 * server li ricalcola per conto suo. Accettarli come verita' significherebbe fidarsi di un
 * client per decidere cosa esce sull'antenna. Jackson li scarta, perche' in Spring Boot i
 * campi sconosciuti non sono un errore.
 */
data class SensorConfigCommandSetDTO(
        /** DevEUI dichiarato dal client: serve solo a riconoscere una pagina aperta sulla CU sbagliata. */
        val devEui: String? = null,
        /**
         * CFG_VER su cui il client ha lavorato. **Non si scrive**: il contatore lo muove il
         * server. Si confronta soltanto, per accorgersi che la pagina ragionava su una
         * fotografia vecchia.
         */
        val configVersion: Int? = null,
        /**
         * Periodo di trasmissione della CU, come indice di scala. Null = lascialo com'e'.
         *
         * Sta qui, e non soltanto nel comando dedicato, perche' la 0x21 lo **trasporta
         * gia'**: e' il quarto byte del blocco 0, come valore assoluto. Mandarlo insieme
         * alla configurazione non costa nessun downlink in piu', mentre impostarlo prima
         * con la 0x24 ne costa uno e un CMD_SEQ — e costava soprattutto un passo che
         * l'interfaccia non riusciva a raccontare: il cursore scelto e non applicato
         * restava nel browser, e la configurazione leggeva lo zero sul database.
         *
         * Lo zero non si accetta da qui. Sulla scala vale «non trasmettere», e fermare una
         * CU non e' un effetto collaterale che si subisce configurando dei sensori: per
         * quello c'e' il comando della trasmissione, dove la scelta e' esplicita.
         */
        val transmissionIndex: Int? = null,
        val sampling: List<SamplingCommandDTO> = emptyList(),
        val processing: List<ProcessingCommandDTO> = emptyList(),
        val thresholds: List<ThresholdCommandDTO> = emptyList(),
)

/**
 * Chi riceve il comando.
 *
 * - `broadcast`: tutti i sensori configurabili della CU
 * - `mu`: tutti i sensori di una MU, per Local ID
 * - `bitmap`: 48 bit in esadecimale, un bit per sensore nell'ordine canonico
 *   (LID crescente, poi indice di slot); il bit meno significativo e' il primo sensore
 * - `list`: elenco esplicito di coppie (LID, indice)
 */
data class SensorSelectorDTO(
        val kind: String,
        val muLocalId: Int? = null,
        val bitmapHex: String? = null,
        val sensors: List<SensorRefDTO> = emptyList(),
)

data class SensorRefDTO(
        val muLocalId: Int,
        val sensorIndex: Int,
)

/** Periodo di campionamento: indice sulla scala non lineare, 0 = sensore spento. */
data class SamplingCommandDTO(
        val selector: SensorSelectorDTO,
        val periodIndex: Int,
)

/**
 * Funzioni di elaborazione a bordo MU.
 *
 * `measure` e' l'identificatore dell'interfaccia (`avg`, `mm`, `int`, `med`, `pct`, `pt`):
 * la traduzione nella `Stat bitmap` del protocollo la fa [com.polito.tesi.measuremanager.template.StatBitmap],
 * che e' anche il posto da cui il decoder legge la stessa mappa al contrario.
 */
data class ProcessingCommandDTO(
        val selector: SensorSelectorDTO,
        val measure: String,
        /** Percentile richiesto, solo con `measure = "pct"`. */
        val percentile: Int? = null,
        val alarms: AlarmFlagsDTO? = null,
)

/** Quali allarmi l'utente vuole armati. Oggi il server li registra e non li trasmette. */
data class AlarmFlagsDTO(
        val thresholdHigh: Boolean = false,
        val thresholdLow: Boolean = false,
        val rateOfChange: Boolean = false,
        val timeOutOfRange: Boolean = false,
        val cumulativeExposure: Boolean = false,
)

/**
 * Valori delle soglie, **in grandezza fisica**.
 *
 * Sul filo viaggiano grezzi, quindi servirebbe l'inversione della taratura: e' il lavoro
 * registrato come rimandato in `STATO_LAVORI.md`. Finche' non c'e', questi valori si
 * conservano e non si spediscono, e la risposta lo dichiara invece di tacerlo.
 */
data class ThresholdCommandDTO(
        val selector: SensorSelectorDTO,
        val fields: Map<String, Double> = emptyMap(),
)

/**
 * Cosa il server ha fatto davvero.
 *
 * Non e' un «ok»: e' l'elenco dei comandi partiti e di quello che non e' partito, con il
 * motivo. Una interfaccia che festeggia sempre e' peggio di una che non fa niente, perche'
 * toglie all'utente l'unico indizio che aveva.
 */
data class SensorConfigResultDTO(
        val controlUnitId: Long,
        val deviceId: String,
        /** Comandi effettivamente composti e consegnati a Kafka. */
        val commands: List<SentCommandDTO>,
        /** CFG_VER atteso dopo che la CU avra' applicato tutto. */
        val configVersion: Long,
        /** Ultimo CMD_SEQ assegnato: e' quello che il poll dovra' restituire. */
        val cmdSeq: Int,
        /** Cosa non e' stato applicato, e perche'. Vuoto quando e' andato tutto. */
        val warnings: List<String> = emptyList(),
)

data class SentCommandDTO(
        /** FPort, che nella v1.2 e' l'opcode: 0x21 periodi, 0x23 statistiche e soglie. */
        val fPort: Int,
        val cmdSeq: Int,
        /** Pacchetti generati: piu' d'uno quando il comando non sta in 51 byte. */
        val packets: Int,
        val bytes: Int,
        /** Quanti sensori tocca. */
        val sensors: Int,
)
