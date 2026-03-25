package com.polito.tesi.measuremanager.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity
@Inheritance(strategy = InheritanceType.JOINED) // Crea tabelle separate per i dati specifici
abstract class Sensor(
    @Id @GeneratedValue var id: Long = 0,
    var modelName: String,
    @ManyToOne @JoinColumn(name = "mu_id") var measurementUnit: MeasurementUnit?,
    var sensorIndex: Int = 0,

    var PhysVal: Double = -40.0,
    var ElecVal: Double = 0.0,
    var SamplingF: Double = 1000.0,
    var PhyThreshold: Double = -40.0,
    var isUpperThresholdMax: Boolean = false, //Se è superiore threshold superiore
    var isLowerThresholdMin: Boolean = false, //Se è inferiore alla threshold minore

)

@Entity
class NTCTemperatureSensor : Sensor(modelName = "NTCTemperatureSensor", measurementUnit = null) {

    // Template params
    var templateId: Int = 201
    var unitCode: Int = 1 // es. 1 = °C

    // Sensor params
    var minPhysVal: Double = -40.0
    var maxPhysVal: Double = 105.0
    //var PhysValSI
    var minElecVal: Double = 0.0
    var maxElecVal: Double = 0.0
    //Var ElecValSI

    var maxSamplingF: Double = 1000.0 //Hz
    var minSamplingF: Double = 0.000001
    //var SampligFSI

    var minPhyThreshold: Double = -40.0
    var maxPhyThreshold: Double = 105.0
    //var PhyThreshold

    var readingConsumption: Double = 0.5e-9 // C (da verificare meglio: 1mA consumo ADC * T_convrsione = 15 cycle * 32 MHz) = 0.5 nC
    // var readingConsumptionSI inserire unità di misura

    // Sensor dependent param
    var refResOhm: Double = 100000.0
    var refTempC: Double = 25.0

    var responseTime: Double = 0.16 // s/°C
    //Var responseTimeSI

    var selfHeat: Double = 0.0
    var exciteNom: Double = 0.0
    var exciteMax: Double = 0.0

    // Metrology params
    var absUncertainty: Double = 5.0 //LSB
    //var absUncSI
    var UncertaintyPDF: String = "uniform"
    var uB: Double = 2.9 //LSB
    //var uBSI
    var K: Double = 2.0  //Coverage factor

    var coeffA: Double = 0.0
    //var coeffASI
    var coeffB: Double = 0.0
    //var coeffBSI
    var coeffC: Double = 0.0
    //var coeffCSI
    var coeffD: Double = 0.0
    //var coeffDSI

    var measLocId: Long = 0L
    var calDate: Long = 0L
    var calPeriod: Int = 365

    // ??
    @Column(length = 250)
    var calInitials: String? = null



}

@Entity
class PressureSensor : Sensor(modelName = "MS5837", measurementUnit = null) {
    var templateId: Int = 202
    var unitCode: Int = 2 // es. 2 = mbar o Pascal

    // Sensor params
    var minPhysVal: Double = 0.0
    var maxPhysVal: Double = 30000.0 // es. 3000 mbar per la versione 30BA
    //var PhysValSI

    var minElecVal: Double = 0.0
    var maxElecVal: Double = 0.0
    //Var ElecValSI

    var maxSamplingF: Double = 50.0 //Hz
    var minSamplingF: Double = 0.000001 //circa 10 giorni
    //var SampligFSI

    var minPhyThreshold: Double = 0.0
    var maxPhyThreshold: Double = 30000.0
    //var PhyThreshold

    var readingConsumption: Double = 25e-6 // C (1.25mA * 20ms = 25 uC)
    // var readingConsumptionSI inserire unità di misura

    var standbyConsumption: Double = 0.1 //uA
    // var standbyConsumptionSI inserire unità di misura


    // Block B: Metadata
    var calDate: Long = 0L
    var fluidDensity: Double = 1025.0 // kg/m³ (fondamentale per calcolare la profondità in acqua salata)
}

@Entity
class HumiditySensor : Sensor(modelName = "HPP845E", measurementUnit = null) {
    var templateId: Int = 203
    var unitCode: Int = 3 // es. 3 = %RH

    // Block A: Model (Polinomio di calibrazione)
    // RH = CoeffA * V^2 + CoeffB * V + CoeffC
    var coeffA: Double = 0.0
    var coeffB: Double = 0.0
    var coeffC: Double = 0.0

    // Ranges
    var minRH: Double = 0.0
    var maxRH: Double = 100.0

    // Block B: Physics
    var responseTime: Double = 5.0 // Secondi
    var calDate: Long = 0L
}

@Entity
class AccelerometerSensor : Sensor(modelName = "LSM6DSM", measurementUnit = null) {
    var templateId: Int = 204
    var unitCode: Int = 4 // es. 4 = g (gravità)

    // Block A: Sensibilità e Bias per asse (X, Y, Z)
    var sensitivityX: Double = 0.061 // mg/LSB (valore tipico per +/- 2g)
    var sensitivityY: Double = 0.061
    var sensitivityZ: Double = 0.061

    var biasX: Double = 0.0 // Offset di calibrazione
    var biasY: Double = 0.0
    var biasZ: Double = 0.0

    // Configurazione hardware
    var fullScaleRange: Int = 2 // 2, 4, 8, o 16 g
    var outputDataRate: Double = 104.0 // Hz

    // Block B: Metadata
    var calDate: Long = 0L
}
