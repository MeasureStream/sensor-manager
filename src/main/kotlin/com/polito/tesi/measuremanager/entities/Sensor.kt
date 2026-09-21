package com.polito.tesi.measuremanager.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity
class Sensor(
        @Id @GeneratedValue var id: Long = 0,
        // Chiave logica → matcha il nome file JSON
        var modelName: String,
        @ManyToOne @JoinColumn(name = "mu_id") var measurementUnit: MeasurementUnit?,
        var sensorIndex: Int = 0,
        /**
         * Asse dello slot ("X", "Y", "Z") quando il modello di MU istanzia piu' volte lo
         * stesso template: il template dei tre assi e' identico, l'identita' dell'asse vive
         * qui. Null per i sensori che non hanno canali.
         */
        @Column(length = 8) var channel: String? = null,
        var setting1: Int = 0,
        var samplingPeriod: Int = 0,

        // Runtime values
        var physVal: Double = 0.0,
        var elecVal: Double = 0.0,
        var samplingF: Double = 0.0,
        var phyThreshold: Double = 0.0,
        var isUpperThresholdMax: Boolean = false,
        var isLowerThresholdMin: Boolean = false,
        var isLowerThresholdActive: Boolean = false,
        var isUpperThresholdActive: Boolean = false,
        var isFlashActive: Boolean = false,
        var isSensorActive: Boolean = false,
        var isStatisticActive: Boolean = false,

        // SOLO se calibrati per singolo sensore reale
        var coeffA: Double? = null,
        var coeffB: Double? = null,
        var coeffC: Double? = null,
        var coeffD: Double? = null,

        // Metadata reali
        var calDate: Long? = null,
        var measLocId: Long? = null,
        @Column(length = 250) var calInitials: String? = null,

        // configurations
        var configurationMeasure: String
)
