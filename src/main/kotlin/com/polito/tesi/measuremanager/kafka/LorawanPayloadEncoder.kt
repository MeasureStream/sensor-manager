package com.polito.tesi.measuremanager.kafka

import com.polito.tesi.measuremanager.dtos.CUConfigurationDTO
import com.polito.tesi.measuremanager.dtos.CUTransmissionCommandDTO
import com.polito.tesi.measuremanager.uplink.FPort
import java.io.ByteArrayOutputStream
import org.springframework.stereotype.Component

/**
 * Composizione dei downlink del protocollo v1.2.
 *
 * Due cose cambiano rispetto alla revisione precedente, e cambiano per tutti i comandi:
 *
 * 1. **L'opcode non sta piu' nel payload.** Lo porta la FPort, che e' gia' un campo del
 *    frame LoRaWAN: ripeterlo dentro costava un byte su ogni comando senza aggiungere nulla.
 * 2. **I quattro comandi di configurazione hanno un prologo di blocco** con CMD_SEQ, che
 *    rende riconoscibili i duplicati e impedisce che una configurazione entri in opera a
 *    meta' quando serve piu' di un pacchetto.
 *
 * Ogni metodo restituisce una **lista** di payload: uno solo nel caso normale, piu' d'uno
 * quando lo stream dei periodi non sta in 51 byte. Vanno inviati tutti, in ordine.
 */
@Component
class LorawanPayloadEncoder {

    /**
     * Configurazione CU 0x0A (§ 4.14). Quattro byte, nessun prologo: trasporta valori
     * assoluti, quindi riapplicarla e' innocuo e non serve rilevare i duplicati.
     *
     * @param pollingHours periodo di poll in ore; 0 e' lo spegnimento definitivo, irreversibile
     * @param numPacket finestre RX aggiuntive da aprire, massimo 10
     */
    fun encodeCuConfig(
            pollingHours: Int,
            numPacket: Int = 0,
            setting1: Int = 0,
            command1: Int = 0,
    ): List<EncodedPayload> {
        val bytes =
                byteArrayOf(
                        (numPacket.coerceIn(0, 10) and 0xFF).toByte(),
                        (pollingHours and 0xFF).toByte(),
                        (setting1 and 0xFF).toByte(),
                        (command1 and 0xFF).toByte(),
                )
        return listOf(EncodedPayload(bytes, fPort = FPort.CU_CONFIG))
    }

    /**
     * Configurazione periodi 0x21 (§ 4.15): periodo di trasmissione della CU e stream dei
     * periodi di campionamento, in ordine di LID della MU e di slot.
     *
     * `CU Trans. T` e' presente **solo nel blocco 0**.
     */
    fun encodePeriods(
            config: CUConfigurationDTO,
            cmdSeq: Int,
            transmissionIndex: Int,
    ): List<EncodedPayload> {
        val stream = samplingStream(config)
        // Blocco 0: BLOCK + CMD_SEQ + OFFSET + CU Trans. T. Gli altri non hanno l'ultimo.
        val blocks = BlockPrologue.split(stream, headerSize = 4)

        return blocks.mapIndexed { index, (offset, chunk) ->
            val out = ByteArrayOutputStream()
            out.write(BlockPrologue.block(blocks.size, index))
            out.write(cmdSeq and 0xFF)
            out.write(offset and 0xFF)
            if (index == 0) out.write(transmissionIndex and 0xFF)
            chunk.forEach { out.write(it and 0xFF) }
            EncodedPayload(out.toByteArray(), fPort = FPort.PERIODS)
        }
    }

    /**
     * Programmazione completa 0x22 (§ 4.16): finestra di acquisizione, periodo di
     * trasmissione e periodi di campionamento in un colpo solo.
     *
     * @param startHours ritardo da adesso all'avvio, in ore; 0 = avvio immediato
     * @param stopHours durata dalla partenza, in ore; 0 = nessuna fine programmata
     */
    fun encodeSchedule(
            config: CUConfigurationDTO,
            cmdSeq: Int,
            transmissionIndex: Int,
            startHours: Int,
            stopHours: Int,
    ): List<EncodedPayload> {
        val stream = samplingStream(config)
        val blocks = BlockPrologue.split(stream, headerSize = 8)

        return blocks.mapIndexed { index, (offset, chunk) ->
            val out = ByteArrayOutputStream()
            out.write(BlockPrologue.block(blocks.size, index))
            out.write(cmdSeq and 0xFF)
            out.write(offset and 0xFF)
            writeUInt16(out, startHours)
            writeUInt16(out, stopHours)
            out.write(transmissionIndex and 0xFF)
            chunk.forEach { out.write(it and 0xFF) }
            EncodedPayload(out.toByteArray(), fPort = FPort.SCHEDULE)
        }
    }

    /**
     * Programmazione breve 0x24 (§ 4.17): come la 0x22 ma senza i periodi di campionamento,
     * che restano quelli dell'ultima 0x21. Sette byte indipendenti dal numero di sensori,
     * quindi un solo pacchetto anche con 48 sensori a DR0.
     */
    fun encodeShortSchedule(
            cmdSeq: Int,
            transmissionIndex: Int,
            startHours: Int,
            stopHours: Int,
    ): List<EncodedPayload> {
        val out = ByteArrayOutputStream()
        out.write(BlockPrologue.SINGLE)
        out.write(cmdSeq and 0xFF)
        writeUInt16(out, startHours)
        writeUInt16(out, stopHours)
        out.write(transmissionIndex and 0xFF)
        return listOf(EncodedPayload(out.toByteArray(), fPort = FPort.SHORT_SCHEDULE))
    }

    /**
     * Statistiche e soglie 0x23 (§ 4.18): record concatenati, uno per sensore, ciascuno
     * autodescrittivo. Si configurano solo i sensori che cambiano, non tutta la flotta.
     *
     * Ogni soglia viaggia **solo se il bit che la governa e' alzato** in `Alarm enable`,
     * quindi il record va da 4 a 12 byte.
     */
    fun encodeStatsAndThresholds(cmdSeq: Int, records: List<SensorStatsRecord>): List<EncodedPayload> {
        val encoded = records.map { it.toBytes() }
        val payloads = mutableListOf<EncodedPayload>()

        // I record non si spezzano a meta': si riempie un blocco finche' c'e' posto.
        var current = mutableListOf<List<Int>>()
        var size = 3 // BLOCK + CMD_SEQ + N_REC

        fun flush() {
            if (current.isEmpty()) return
            payloads.add(EncodedPayload(buildStatsBlock(cmdSeq, current), fPort = FPort.STATS))
            current = mutableListOf()
            size = 3
        }

        encoded.forEach { record ->
            if (size + record.size > BlockPrologue.MAX_PAYLOAD) flush()
            current.add(record)
            size += record.size
        }
        flush()

        // Il byte BLOCK si scrive ora che si sa quanti blocchi sono.
        return payloads.mapIndexed { index, payload ->
            val bytes = payload.bytes.copyOf()
            bytes[0] = BlockPrologue.block(payloads.size, index).toByte()
            EncodedPayload(bytes, payload.fPort)
        }
    }

    private fun buildStatsBlock(cmdSeq: Int, records: List<List<Int>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(BlockPrologue.SINGLE) // riscritto dopo, quando si sa il totale
        out.write(cmdSeq and 0xFF)
        out.write(records.size and 0xFF)
        records.forEach { record -> record.forEach { out.write(it and 0xFF) } }
        return out.toByteArray()
    }

    /** Lo stream dei periodi di campionamento, in ordine di LID e di slot. */
    private fun samplingStream(config: CUConfigurationDTO): List<Int> =
            config.configurations.sortedBy { it.localId }.flatMap { mu ->
                mu.sensors.sortedBy { it.sensorIndex }.map { it.samplingPeriod and 0xFF }
            }

    private fun writeUInt16(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    /**
     * Un record della 0x23. Le soglie sono nullable perche' la loro presenza sul filo dipende
     * dai bit di [alarmEnable]: dichiararle opzionali qui rende impossibile scriverne una
     * senza averla armata.
     */
    data class SensorStatsRecord(
            /** Indice globale del sensore: somma cumulativa in ordine di LID e di slot. */
            val globalIndex: Int,
            /** Due byte, un bit per metrica nell'ordine canonico. */
            val statBitmap: Int,
            /** Un byte, stessa numerazione di ALARM_WORD. */
            val alarmEnable: Int,
            val thresholdHigh: Int? = null,
            val thresholdLow: Int? = null,
            val rocHigh: Int? = null,
            val rocLow: Int? = null,
    ) {
        fun toBytes(): List<Int> {
            val bytes = mutableListOf<Int>()
            bytes.add(globalIndex and 0xFF)
            bytes.add((statBitmap shr 8) and 0xFF)
            bytes.add(statBitmap and 0xFF)
            bytes.add(alarmEnable and 0xFF)

            // L'ordine e' quello dei bit: alta, bassa, RoC alto, RoC basso.
            fun maybe(bit: Int, value: Int?) {
                if (alarmEnable and (1 shl bit) == 0) return
                val v = value ?: 0
                bytes.add((v shr 8) and 0xFF)
                bytes.add(v and 0xFF)
            }
            maybe(0, thresholdHigh)
            maybe(1, thresholdLow)
            maybe(2, rocHigh)
            maybe(3, rocLow)
            return bytes
        }
    }

    /** Ponte con il vecchio chiamante del comando di trasmissione: usa la 0x24. */
    fun encodeTransmissionConfig(config: CUTransmissionCommandDTO, cmdSeq: Int): List<EncodedPayload> =
            encodeShortSchedule(
                    cmdSeq = cmdSeq,
                    transmissionIndex = config.transmissionIndex,
                    startHours = 0,
                    stopHours = 0,
            )
}
