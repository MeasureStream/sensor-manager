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
        /** TEMPLATE_VER del vecchio poll a 7 byte, sostituito da ProtoVer nella v1.2. */
        val templateVersion: Int? = null,
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
 * Il poll, nei formati che possono arrivare.
 *
 * La porta 0x0B e' condivisa da tutte le revisioni del poll: si distinguono dalla lunghezza,
 * che e' il criterio che la documentazione indica. La v1.2 ne ha due, perche' `CMD_SEQ` e'
 * entrato dopo: dieci byte con, nove senza. La 0x0A resta finche' esiste una CU non
 * aggiornata.
 *
 * Un caso resta ambiguo e vale la pena dirlo: un poll della revisione senza `CMD_SEQ` che
 * porti **anche** un byte di estensione dello Status arriva a dieci byte, e viene letto come
 * se il `CMD_SEQ` ci fosse. Sniffare il valore di ProtoVer non aiuterebbe — nel formato nuovo
 * quel byte e' il `CMD_SEQ`, che 0x12 puo' valerlo benissimo. Si accetta perche' la revisione
 * a nove byte e' vissuta pochi giorni, fra il server e il simulatore, e sparisce con
 * l'aggiornamento del firmware.
 */
@Component
class PollDecoder(
        private val protocol: ProtocolService,
) {

    fun decode(bytes: ByteArray, fport: Int): PollMessage {
        val buffer = ByteBuffer.wrap(bytes)

        return when {
            fport == FPort.CU_STATUS_LEGACY -> decodeLegacyStatus(buffer, bytes.size)
            bytes.size >= 10 -> decodeV12(buffer, withCmdSeq = true)
            bytes.size == 9 -> decodeV12(buffer, withCmdSeq = false)
            bytes.size >= 7 -> decodeLegacyPoll(buffer)
            else ->
                    throw PollPayloadError(
                            "${FPort.label(fport)}: ${bytes.size} byte, ne servono almeno 7"
                    )
        }
    }

    /**
     * Poll v1.2: CU Model, CFG_VER, CMD_SEQ, ProtoVer, Battery, P_TX, Status (2 byte),
     * ALARM_SEQ. Senza [withCmdSeq] e' la revisione precedente, a nove byte.
     */
    private fun decodeV12(buffer: ByteBuffer, withCmdSeq: Boolean): PollMessage {
        val model = buffer.short.toInt() and 0xFFFF
        val cfgVersion = buffer.get().toInt() and 0xFF
        val appliedCmdSeq = if (withCmdSeq) buffer.get().toInt() and 0xFF else null
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

    /** Poll precedente alla v1.2: sette byte, con TEMPLATE_VER al posto di ProtoVer. */
    private fun decodeLegacyPoll(buffer: ByteBuffer): PollMessage {
        val model = buffer.short.toInt() and 0xFFFF
        val cfgVersion = buffer.get().toInt() and 0xFF
        val templateVersion = buffer.get().toInt() and 0xFF
        val batteryRaw = buffer.get().toInt() and 0xFF
        val ptx = buffer.get().toInt() and 0xFF
        val status = if (buffer.remaining() >= 1) (buffer.get().toInt() and 0xFF).toLong() else 0L

        return battery(batteryRaw)
                .copy(
                        model = model,
                        cfgVersion = cfgVersion,
                        templateVersion = templateVersion,
                        transmissionPower = ptx,
                        statusWord = status,
                )
    }

    /** Stato sulla 0x0A: cinque byte, senza nessuna versione. */
    private fun decodeLegacyStatus(buffer: ByteBuffer, size: Int): PollMessage {
        if (size < 4) throw PollPayloadError("${FPort.label(FPort.CU_STATUS_LEGACY)}: $size byte")
        val model = buffer.short.toInt() and 0xFFFF
        val batteryRaw = buffer.get().toInt() and 0xFF
        val ptx = buffer.get().toInt() and 0xFF
        val status = if (buffer.remaining() >= 1) (buffer.get().toInt() and 0xFF).toLong() else 0L

        return battery(batteryRaw)
                .copy(model = model, transmissionPower = ptx, statusWord = status)
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
