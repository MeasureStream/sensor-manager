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
)

@Entity
class NTCSensor : Sensor(modelName = "NTC", measurementUnit = null) {
    var templateId: Int = 201

    var unitCode: Int = 1 // es. 1 = °C

    var minPhysVal: Double = 0.0
    var maxPhysVal: Double = 0.0
    var minElecVal: Double = 0.0
    var maxElecVal: Double = 0.0

    var coeffA: Double = 0.0
    var coeffB: Double = 0.0
    var coeffC: Double = 0.0

    var calDate: Long = 0L
    var calPeriod: Int = 365

    @Column(length = 250)
    var calInitials: String? = null

    var refResOhm: Double = 100000.0
    var refTempC: Double = 25.0
    var responseTime: Double = 0.0
    var selfHeat: Double = 0.0

    var exciteNom: Double = 0.0
    var exciteMax: Double = 0.0

    var measLocId: Long = 0L
}

@Entity
class PressureSensor : Sensor(modelName = "MS5837", measurementUnit = null) {
    var templateId: Int = 202
    var unitCode: Int = 2 // es. 2 = mbar o Pascal
    // Block A: Calibration Coefficients (C1-C6 dalla PROM del sensore)
    var coeffC1: Int = 0 // Pressure sensitivity
    var coeffC2: Int = 0 // Pressure offset
    var coeffC3: Int = 0 // Temp coeff of pressure sensitivity
    var coeffC4: Int = 0 // Temp coeff of pressure offset
    var coeffC5: Int = 0 // Reference temperature
    var coeffC6: Int = 0 // Temp coeff of temperature

    // Ranges
    var minPressure: Double = 0.0
    var maxPressure: Double = 3000.0 // es. 3000 mbar per la versione 30BA

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
