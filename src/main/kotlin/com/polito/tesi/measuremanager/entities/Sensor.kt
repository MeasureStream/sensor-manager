package com.polito.tesi.measuremanager.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.OffsetDateTime

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

        // Deprecati (PIANO_METROLOGIA 1.15): nessuno li legge per convertire. La taratura di un
        // esemplare arriva da metrology-service in `active_calibration`, con la sua funzione e
        // la sua covarianza. Restano solo per compatibilita' del DTO; si tolgono nella fase 7.
        var coeffA: Double? = null,
        var coeffB: Double? = null,
        var coeffC: Double? = null,
        var coeffD: Double? = null,

        // Metadata reali
        var calDate: Long? = null,
        var measLocId: Long? = null,
        @Column(length = 250) var calInitials: String? = null,

        // configurations
        var configurationMeasure: String,

        /**
         * Istante dell'ultima misura registrata da questo sensore.
         *
         * Sta accanto a `physVal` ed `elecVal` perche' e' la stessa cosa: il quando dei
         * valori che quelle due colonne tengono. Si scrive nella transazione che li
         * aggiorna, quindi non costa una query, e in lettura evita di chiedere a
         * `metric_sample` il massimo per sensore a ogni apertura di una scheda.
         *
         * Null finche' il sensore non ha prodotto una misura buona: le sentinelle
         * «nessun dato nuovo» e «non risponde» non contano, non sono misure.
         */
        var lastValueAt: OffsetDateTime? = null,
)
