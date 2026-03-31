package com.polito.tesi.measuremanager.hmac

import org.hashids.Hashids

object NetworkIdEncoder {
    private val hashids = Hashids("il_tuo_salt_segreto", 8) // Lunghezza minima 8 caratteri

    fun encode(networkId: Long): String {
        return hashids.encode(networkId)
    }

    fun decode(hash: String): Long {
        val decoded = hashids.decode(hash)
        if (decoded.isEmpty()) throw IllegalArgumentException("Hash non valido")
        return decoded[0]
    }
}
