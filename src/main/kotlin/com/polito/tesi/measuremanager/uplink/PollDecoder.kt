package com.polito.tesi.measuremanager.uplink

import com.polito.tesi.measuremanager.template.ProtocolService
import java.nio.ByteBuffer
import org.springframework.stereotype.Component

/** Il poll letto: quello che la CU racconta di se' a ogni contatto. */
data class PollMessage(
        val model: Int,
        /** CFG_VER dichiarato dalla CU. Assente nel formato 0x0A, che non lo portava. */
        val cfgVersion: Int? = null,
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
 * Il poll, nei tre formati che possono arrivare.
 *
 * La porta 0x0B e' condivisa fra il poll della v1.2 e quello che lo precedeva: si distinguono
 * dalla lunghezza, che e' l'unico criterio possibile e quello che la documentazione indica.
 * La 0x0A resta finche' esiste una CU che non e' stata aggiornata.
 */
@Component
class PollDecoder(
        private val protocol: ProtocolService,
) {

    fun decode(bytes: ByteArray, fport: Int): PollMessage {
        val buffer = ByteBuffer.wrap(bytes)

        return when {
            fport == FPort.CU_STATUS_LEGACY -> decodeLegacyStatus(buffer, bytes.size)
            bytes.size >= 9 -> decodeV12(buffer)
            bytes.size >= 7 -> decodeLegacyPoll(buffer)
            else ->
                    throw PollPayloadError(
                            "${FPort.label(fport)}: ${bytes.size} byte, ne servono almeno 7"
                    )
        }
    }

    /** Poll v1.2: CU Model, CFG_VER, ProtoVer, Battery, P_TX, Status (2 byte), ALARM_SEQ. */
    private fun decodeV12(buffer: ByteBuffer): PollMessage {
        val model = buffer.short.toInt() and 0xFFFF
        val cfgVersion = buffer.get().toInt() and 0xFF
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
