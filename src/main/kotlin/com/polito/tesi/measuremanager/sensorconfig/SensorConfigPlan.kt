package com.polito.tesi.measuremanager.sensorconfig

import com.polito.tesi.measuremanager.entities.Sensor

/**
 * Un sensore nella posizione che gli spetta sul filo.
 *
 * L'ordine canonico — MU per Local ID crescente, poi slot per indice crescente — non e' una
 * convenzione interna: e' l'ordine in cui la 0x21 elenca i periodi e la numerazione con cui
 * la 0x23 indirizza i record. Due componenti che lo ricostruiscono per conto proprio sono due
 * occasioni di sbagliarlo, quindi lo costruisce solo [SensorConfigPlanner] e tutti gli altri
 * ricevono questa lista gia' fatta.
 */
data class ConfigurableSensor(
        /** Posizione nell'ordine canonico: e' l'indirizzo del sensore nel record della 0x23. */
        val globalIndex: Int,
        val muLocalId: Int,
        val sensorIndex: Int,
        val sensor: Sensor,
        /**
         * Falso per i sensori oltre il limite della CU: esistono, si vedono, ma non si
         * configurano, e il loro periodo sul filo resta zero.
         */
        val configurable: Boolean,
) {
    /** Come lo si nomina in un avviso: «MU 1, slot 3 (TemperatureSensorNTC)». */
    val label: String
        get() = "MU $muLocalId, slot $sensorIndex (${sensor.modelName})"
}

/**
 * Lo stato desiderato di un sensore dopo aver applicato i comandi della richiesta.
 *
 * Si parte sempre da cio' che c'e' nel database e si applicano sopra le modifiche: il
 * frontend manda **solo le righe cambiate**, ma la 0x21 e' un comando di stato pieno, che
 * elenca il periodo di ogni sensore. Senza questa fusione, configurare un sensore
 * spegnerebbe tutti gli altri.
 */
data class DesiredSensorState(
        val target: ConfigurableSensor,
        /** Indice sulla scala di campionamento; 0 = spento. */
        var periodIndex: Int,
        /** Nome canonico della misura, come si conserva in `Sensor.configurationMeasure`. */
        var measure: String,
        var periodChanged: Boolean = false,
        var measureChanged: Boolean = false,
)

/**
 * Il risultato della pianificazione: cosa va scritto, cosa va spedito, cosa non si puo' fare.
 *
 * Gli avvisi sono frasi gia' leggibili e non chiavi di traduzione: nascono a runtime da cio'
 * che la richiesta chiedeva e dalla topologia del momento, quindi non possono stare nel
 * dizionario dell'interfaccia, esattamente come le descrizioni che arrivano dai template.
 */
data class SensorConfigPlan(
        /** Tutti i sensori della CU nell'ordine canonico, con lo stato desiderato. */
        val sensors: List<DesiredSensorState>,
        val warnings: List<String>,
) {
    /** Lo stream completo dei periodi: e' il corpo della 0x21. */
    val periodStream: List<Int>
        get() = sensors.map { it.periodIndex }

    val periodsChanged: Boolean
        get() = sensors.any { it.periodChanged }

    /** Solo i sensori la cui elaborazione cambia: la 0x23 configura chi cambia, non la flotta. */
    val changedMeasures: List<DesiredSensorState>
        get() = sensors.filter { it.measureChanged }

    val hasWork: Boolean
        get() = periodsChanged || changedMeasures.isNotEmpty()
}
