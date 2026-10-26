package com.polito.tesi.measuremanager.uplink

import com.polito.tesi.measuremanager.template.ProtocolService
import java.nio.ByteBuffer
import org.springframework.stereotype.Component

/** Il poll letto: quello che la CU racconta di se' a ogni contatto. */
data class PollMessage(
        val model: Int,
        /** CFG_VER dichiarato dalla CU. Assente nel formato 0x0A, che non lo portava. */
        val cfgVersion: Int? = null,
        /**
         * CMD_SEQ dell'ultimo comando di configurazione **applicato**.
         *
         * E' il numero che ha scritto il server, nel prologo di blocco, e che la CU si limita
         * a restituire: ha un autore solo, quindi non puo' desincronizzarsi. Il CFG_VER
         * invece lo incrementa la CU e il server deve prevederlo — due contatori con due
         * autori restano in passo solo finche' ogni comando arriva e viene applicato una
         * volta sola. Questo byte dice **quale** comando e' in opera, e toglie al server il
         * bisogno di indovinarlo.
         */
        val appliedCmdSeq: Int? = null,
        /** ProtoVer: 0x12 per il protocollo v1.2. Assente nei formati precedenti. */
        val protocolVer: Int? = null,
        val batteryRaw: Int,
        val batteryLevel: Int,
        val acPowered: Boolean,
        val charging: Boolean,
        val transmissionPower: Int,
        /** I bit di stato, senza il bit di continuazione e con la catena gia' composta. */
        val statusWord: Long,
        /** ALARM_SEQ corrente della CU: c'e' solo nella v1.2. */
        val alarmSeq: Int? = null,
)

/** Il payload del poll non e' leggibile. */
class PollPayloadError(message: String) : Exception(message)

/**
 * Il poll della v1.2, e nient'altro.
 *
 * I formati precedenti sono stati dismessi il 26 ottobre 2026, quando ogni CU in campo ha
 * dichiarato `ProtoVer` 0x12: il poll a sette byte con TEMPLATE_VER, lo stato a cinque byte
 * sulla 0x0A e la revisione intermedia a nove byte non si leggono piu'.
 *
 * Non e' solo pulizia. Finche' due formati convivono sulla stessa porta e si distinguono
 * dalla lunghezza, un payload troncato cade sul ramo corto e viene letto come se fosse valido:
 * i campi scalano di posizione e il risultato e' plausibile. Con un formato solo, un poll
 * malformato e' un errore dichiarato.
 */
@Component
class PollDecoder(
        private val protocol: ProtocolService,
) {

    fun decode(bytes: ByteArray, fport: Int): PollMessage {
        val buffer = ByteBuffer.wrap(bytes)

        if (bytes.size < 10) {
            throw PollPayloadError(
                    "${FPort.label(fport)}: ${bytes.size} byte, il poll v1.2 ne ha almeno 10"
            )
        }
        return decodeV12(buffer)
    }

    /**
     * Poll v1.2: CU Model, CFG_VER, CMD_SEQ, ProtoVer, Battery, P_TX, Status (2 byte),
     * ALARM_SEQ.
     */
    private fun decodeV12(buffer: ByteBuffer): PollMessage {
        val model = buffer.short.toInt() and 0xFFFF
        val cfgVersion = buffer.get().toInt() and 0xFF
        val appliedCmdSeq = buffer.get().toInt() and 0xFF
        val protocolVer = buffer.get().toInt() and 0xFF
        val batteryRaw = buffer.get().toInt() and 0xFF
        val ptx = buffer.get().toInt() and 0xFF
        // Lo Status puo' portare una catena di estensione: la lunghezza del poll non e'
        // fissa, e l'ALARM_SEQ sta comunque in fondo.
        val status = Continuation.read16(buffer)
        val alarmSeq = if (buffer.remaining() >= 1) buffer.get().toInt() and 0xFF else null

        return battery(batteryRaw)
                .copy(
                        model = model,
                        cfgVersion = cfgVersion,
                        appliedCmdSeq = appliedCmdSeq,
                        protocolVer = protocolVer,
                        transmissionPower = ptx,
                        statusWord = status,
                        alarmSeq = alarmSeq,
                )
    }

    /**
     * La batteria non e' sempre una percentuale: due valori sono stati, e quali siano lo dice
     * il dizionario di protocollo, non una costante scritta qui.
     */
    private fun battery(raw: Int): PollMessage {
        val meaning = protocol.batteryMeaning(raw)
        return PollMessage(
                model = 0,
                batteryRaw = raw,
                // Alimentata o in carica: la carica residua non e' un'informazione, e 254
                // salvato come percentuale sarebbe una batteria al 254%.
                batteryLevel = if (meaning != null) 100 else raw.coerceIn(0, 100),
                acPowered = meaning == "ac_powered",
                charging = meaning == "charging",
                transmissionPower = 0,
                statusWord = 0,
        )
    }
}
