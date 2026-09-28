package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.template.StatusBit

/**
 * Un bit di stato alzato, gia' tradotto.
 *
 * Il testo non viaggia dal dispositivo e non e' scritto nel codice: nasce dal dizionario di
 * protocollo al momento della lettura. Un bit che domani acquista significato compare
 * pubblicando una versione del dizionario, senza toccare ne' il server ne' l'interfaccia.
 */
data class StatusFlagDTO(
        val bit: Int,
        val meaning: String,
        val description: String,
        /** `state` si spegne da solo, `event` resta finche' il server non lo conferma. */
        val kind: String?,
        /** Per lo stato di una MU: `mu` se il bit arriva dal bus UART, `cu` se lo aggiunge la CU. */
        val origin: String?,
)

fun StatusBit.toDTO() =
        StatusFlagDTO(
                bit = bit,
                meaning = meaning,
                description = description,
                kind = kind,
                origin = origin,
        )
