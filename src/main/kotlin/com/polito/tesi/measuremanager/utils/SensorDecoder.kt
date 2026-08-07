package com.polito.tesi.measuremanager.utils

import kotlin.math.ln
import kotlin.math.pow

object SensorDecoder {

    // Costanti per NTC
    private const val R0 = 100000.0 // 100 kΩ
    private const val B = 4190.0 // Beta = 4190 K
    private const val T0 = 298.15 // 25 °C in Kelvin
    private const val R_SERIES = 50000.0 // 50 kΩ

    // Risultato del parsing di una singola grandezza
    data class DecodedValue(
            val physicalValue: Double?,
            val physicalVariance: Double?,
            val status: String // "OK", "NO_NEW_DATA", "SENSOR_ERROR", "NOT_ACTIVE"
    )

    /** Controlla e decodifica i valori raw di media e varianza per un sensore. */
    fun decode(modelName: String, rawMean: Int, rawVar: Int): DecodedValue {
        // 1. Gestione Valori Sentinella
        if (rawMean == 0xFFFF && rawVar == 0xFFFF) {
            return DecodedValue(null, null, "NO_NEW_DATA") // Trans.T < Sampl.T
        }
        if (rawMean == 0xFFFF && rawVar == 0xFFFF) { // In caso di 0xFFFFFFFF unico
            return DecodedValue(null, null, "SENSOR_ERROR")
        }
        if (rawMean == 0 && rawVar == 0) {
            return DecodedValue(null, null, "NOT_ACTIVE") // Sampl.T = 0
        }

        // 2. Conversione in base al tipo di sensore
        return when (modelName.lowercase()) {
            "pressure_ms5837" -> decodePressure(rawMean, rawVar)
            "humidity_hpp845e", "humidity_htu21d" -> decodeHumidity(rawMean, rawVar)
            "ntc_temperature" -> decodeNtc(rawMean, rawVar)
            "accelerometer_lsm6dsm" -> DecodedValue(0.0, 0.0, "OK") // Sempre 0x0000
            else -> DecodedValue(rawMean.toDouble(), rawVar.toDouble(), "RAW_FALLBACK")
        }
    }

    // --- Pressione MS5837 ---
    private fun decodePressure(rawMean: Int, rawVar: Int): DecodedValue {
        // mbar = raw / 10
        val mbar = rawMean / 10.0
        // var_mbar = var_raw / 100
        val varMbar = rawVar / 100.0
        return DecodedValue(mbar, varMbar, "OK")
    }

    // --- Umidità HTU21D / HPP845E ---
    private fun decodeHumidity(rawMean: Int, rawVar: Int): DecodedValue {
        // Azzeramento dei 2 bit di stato (& 0xFFFC)
        val cleanRaw = rawMean and 0xFFFC
        // RH% = -6 + 125 * raw / 65536
        val rh = -6.0 + (125.0 * cleanRaw / 65536.0)

        // Propagazione Varianza: (125 / 65536)^2
        val scale = 125.0 / 65536.0
        val varRh = rawVar * scale.pow(2)

        return DecodedValue(rh, varRh, "OK")
    }

    // --- Temperatura NTC ---
    private fun decodeNtc(rawMean: Int, rawVar: Int): DecodedValue {
        val tempCelsius =
                rawToNtcTemperature(rawMean) ?: return DecodedValue(null, null, "OUT_OF_BOUNDS")

        // Propagazione Varianza per curva non lineare
        // Valutazione della derivata locale attorno alla media a +/- 1 LSB
        val varCelsius =
                if (rawMean in 1..4094) {
                    val tPlus = rawToNtcTemperature(rawMean + 1) ?: tempCelsius
                    val tMinus = rawToNtcTemperature(rawMean - 1) ?: tempCelsius
                    val derivative = (tPlus - tMinus) / 2.0
                    rawVar * derivative.pow(2)
                } else {
                    0.0
                }

        return DecodedValue(tempCelsius, varCelsius, "OK")
    }

    /** Formula NTC: Converte un ADC raw 12-bit (0..4095) in Temperatura °C */
    private fun rawToNtcTemperature(raw: Int): Double? {
        if (raw <= 0 || raw >= 4095) return null // Evita divisione per zero e overflow

        // R_ntc = R_series * (4095 / raw - 1)
        val rNtc = R_SERIES * ((4095.0 / raw) - 1.0)

        // 1/T = 1/T0 + (1/B) * ln(R_ntc / R0)
        val invT = (1.0 / T0) + (1.0 / B) * ln(rNtc / R0)
        val tempKelvin = 1.0 / invT

        return tempKelvin - 273.15 // Ritorna in gradi Celsius
    }
}
