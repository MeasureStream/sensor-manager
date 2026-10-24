package com.polito.tesi.measuremanager.unitTests.sensorconfig

import com.polito.tesi.measuremanager.dtos.AlarmFlagsDTO
import com.polito.tesi.measuremanager.dtos.ProcessingCommandDTO
import com.polito.tesi.measuremanager.dtos.SamplingCommandDTO
import com.polito.tesi.measuremanager.dtos.SensorConfigCommandSetDTO
import com.polito.tesi.measuremanager.dtos.SensorRefDTO
import com.polito.tesi.measuremanager.dtos.SensorSelectorDTO
import com.polito.tesi.measuremanager.dtos.ThresholdCommandDTO
import com.polito.tesi.measuremanager.entities.ControlUnit
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Sensor
import com.polito.tesi.measuremanager.sensorconfig.SensorConfigPlanner
import com.polito.tesi.measuremanager.template.PeriodScale
import com.polito.tesi.measuremanager.template.PeriodSegment
import com.polito.tesi.measuremanager.template.ProtocolDictionary
import com.polito.tesi.measuremanager.template.ProtocolService
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Il passaggio da comandi raggruppati a record per sensore.
 *
 * Le prove che contano non sono quelle del caso facile: sono quelle in cui l'espansione puo'
 * sbagliare **bersaglio**, perche' un periodo finito sul sensore sbagliato non produce nessun
 * errore e si scopre solo guardando i dati settimane dopo.
 */
class SensorConfigPlannerTest {

    /**
     * Senza dizionario il pianificatore accetta qualunque byte: per quasi tutte le prove va
     * benissimo. Il simulacro e' rilassato perche' `dictionary()` ha un argomento con valore
     * predefinito che passa da `current()`.
     */
    private val protocol =
            mockk<ProtocolService>(relaxed = true) { every { dictionary(any()) } returns null }
    private val planner = SensorConfigPlanner(protocol)

    /** Due MU da quattro slot: otto sensori, indici globali da 0 a 7. */
    private fun cuConDueMu(): ControlUnit {
        val cu = ControlUnit().apply { devEui = 111L; deviceId = "cu-0001001" }
        listOf(1, 2).forEach { localId ->
            val mu =
                    MeasurementUnit().apply {
                        this.localId = localId
                        this.model = 1
                        this.sensors = mutableListOf()
                    }
            listOf("Accelerometro", "Pressione", "Umidita", "Temperatura").forEachIndexed { index, nome ->
                mu.sensors.add(
                        Sensor(
                                modelName = nome,
                                measurementUnit = mu,
                                sensorIndex = index,
                                configurationMeasure = "avg-std",
                        )
                )
            }
            cu.measurementUnits.add(mu)
        }
        return cu
    }

    private fun broadcast() = SensorSelectorDTO(kind = "broadcast")

    @Test
    fun `l'ordine canonico numera i sensori di seguito, MU dopo MU`() {
        val plan = planner.plan(cuConDueMu(), SensorConfigCommandSetDTO())

        assertEquals(8, plan.sensors.size)
        assertEquals(
                listOf(0, 1, 2, 3, 4, 5, 6, 7),
                plan.sensors.map { it.target.globalIndex },
                "l'indice globale e' l'indirizzo del record nella 0x23: non riparte da zero sulla seconda MU",
        )
        assertEquals(
                listOf(1, 1, 1, 1, 2, 2, 2, 2),
                plan.sensors.map { it.target.muLocalId },
        )
    }

    @Test
    fun `la bitmap sceglie i sensori per posizione, non per indice di slot`() {
        // Bit 0 e bit 5: il primo sensore della MU 1 e il secondo della MU 2. Se l'espansione
        // confondesse la posizione con l'indice di slot, colpirebbe due volte la stessa MU.
        val bitmap = SensorSelectorDTO(kind = "bitmap", bitmapHex = "000000000021")

        val plan =
                planner.plan(
                        cuConDueMu(),
                        SensorConfigCommandSetDTO(
                                sampling = listOf(SamplingCommandDTO(bitmap, periodIndex = 65))
                        ),
                )

        val colpiti = plan.sensors.filter { it.periodChanged }
        assertEquals(
                listOf(0 to 1, 5 to 2),
                colpiti.map { it.target.globalIndex to it.target.muLocalId },
        )
    }

    @Test
    fun `il flusso dei periodi resta pieno anche quando cambia un sensore solo`() {
        // La 0x21 elenca TUTTI i periodi: se portasse solo le modifiche, configurare la
        // temperatura spegnerebbe gli altri sette sensori senza che nessuno lo chieda.
        val cu = cuConDueMu()
        cu.measurementUnits.first().sensors.forEach { it.samplingF = 46.0 }

        val soloTemperatura =
                SensorSelectorDTO(
                        kind = "list",
                        sensors = listOf(SensorRefDTO(muLocalId = 1, sensorIndex = 3)),
                )
        val plan =
                planner.plan(
                        cu,
                        SensorConfigCommandSetDTO(
                                sampling = listOf(SamplingCommandDTO(soloTemperatura, periodIndex = 65))
                        ),
                )

        assertEquals(listOf(46, 46, 46, 65, 0, 0, 0, 0), plan.periodStream)
        assertEquals(1, plan.sensors.count { it.periodChanged })
    }

    @Test
    fun `un destinatario che non esiste diventa un avviso, il resto si applica`() {
        val selettore =
                SensorSelectorDTO(
                        kind = "list",
                        sensors =
                                listOf(
                                        SensorRefDTO(muLocalId = 1, sensorIndex = 3),
                                        SensorRefDTO(muLocalId = 9, sensorIndex = 0),
                                ),
                )

        val plan =
                planner.plan(
                        cuConDueMu(),
                        SensorConfigCommandSetDTO(
                                sampling = listOf(SamplingCommandDTO(selettore, periodIndex = 65))
                        ),
                )

        assertEquals(1, plan.sensors.count { it.periodChanged })
        assertTrue(plan.warnings.any { it.contains("MU 9") }, "il destinatario mancante va dichiarato")
    }

    @Test
    fun `rimandare lo stesso valore non produce lavoro`() {
        val cu = cuConDueMu()
        cu.measurementUnits.flatMap { it.sensors }.forEach { it.samplingF = 65.0 }

        val plan =
                planner.plan(
                        cu,
                        SensorConfigCommandSetDTO(
                                sampling = listOf(SamplingCommandDTO(broadcast(), periodIndex = 65)),
                                processing =
                                        listOf(ProcessingCommandDTO(broadcast(), measure = "avg")),
                        ),
                )

        assertFalse(plan.hasWork, "nessuna modifica, nessun downlink: il clic ripetuto non costa aria")
    }

    @Test
    fun `cambiare misura tocca solo i sensori del gruppo`() {
        val plan =
                planner.plan(
                        cuConDueMu(),
                        SensorConfigCommandSetDTO(
                                processing =
                                        listOf(
                                                ProcessingCommandDTO(
                                                        SensorSelectorDTO(kind = "mu", muLocalId = 2),
                                                        measure = "mm",
                                                )
                                        )
                        ),
                )

        val cambiati = plan.changedMeasures
        assertEquals(4, cambiati.size)
        assertTrue(cambiati.all { it.target.muLocalId == 2 })
        assertTrue(cambiati.all { it.measure == "max-min" })
    }

    @Test
    fun `il percentile non ha un campo sul filo e si dichiara, non si finge`() {
        val plan =
                planner.plan(
                        cuConDueMu(),
                        SensorConfigCommandSetDTO(
                                processing =
                                        listOf(
                                                ProcessingCommandDTO(
                                                        broadcast(),
                                                        measure = "pct",
                                                        percentile = 95,
                                                )
                                        )
                        ),
                )

        assertTrue(plan.changedMeasures.isEmpty(), "nessuna misura cambiata")
        assertTrue(plan.warnings.any { it.contains("pct") })
    }

    @Test
    fun `le soglie si conservano ma non partono, e lo dice`() {
        val plan =
                planner.plan(
                        cuConDueMu(),
                        SensorConfigCommandSetDTO(
                                thresholds =
                                        listOf(
                                                ThresholdCommandDTO(
                                                        broadcast(),
                                                        fields = mapOf("thHigh" to 30.0),
                                                )
                                        )
                        ),
                )

        assertTrue(plan.warnings.any { it.contains("Soglie") })
    }

    @Test
    fun `tempo fuori scala ed esposizione cumulata non hanno un bit e si dichiarano`() {
        val plan =
                planner.plan(
                        cuConDueMu(),
                        SensorConfigCommandSetDTO(
                                processing =
                                        listOf(
                                                ProcessingCommandDTO(
                                                        broadcast(),
                                                        measure = "mm",
                                                        alarms = AlarmFlagsDTO(timeOutOfRange = true),
                                                )
                                        )
                        ),
                )

        assertTrue(plan.warnings.any { it.contains("Tempo fuori scala") })
    }

    @Test
    fun `un periodo fuori scala non si applica a meta`() {
        // Una scala minima: un solo segmento, da un minuto a nove.
        val scala =
                PeriodScale(
                        off = 0,
                        minIndex = 0,
                        maxIndex = 73,
                        segments = listOf(PeriodSegment(from = 65, to = 73, base = 60.0, step = 60.0)),
                )
        val protocolloConScala =
                mockk<ProtocolService>(relaxed = true) {
                    every { dictionary(any()) } returns
                            ProtocolDictionary(periods = mapOf("sampling" to scala))
                }

        val plan =
                SensorConfigPlanner(protocolloConScala)
                        .plan(
                                cuConDueMu(),
                                SensorConfigCommandSetDTO(
                                        sampling =
                                                listOf(SamplingCommandDTO(broadcast(), periodIndex = 200))
                                ),
                        )

        assertFalse(plan.periodsChanged, "un indice che la scala non conosce non si manda")
        assertTrue(plan.warnings.any { it.contains("200") })
    }
}
