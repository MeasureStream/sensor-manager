package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.calibration.ActiveCalibrationService
import com.polito.tesi.measuremanager.dtos.SensorCalibrationStatusDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.dtos.toQualityDTO
import com.polito.tesi.measuremanager.entities.MeasurementUnit
import com.polito.tesi.measuremanager.entities.Metric
import com.polito.tesi.measuremanager.quality.QualityFlags
import com.polito.tesi.measuremanager.repositories.MeasurementUnitRepository
import com.polito.tesi.measuremanager.repositories.MetricSampleRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import com.polito.tesi.measuremanager.template.MetrologySpec
import com.polito.tesi.measuremanager.template.TemplateService
import jakarta.persistence.EntityNotFoundException
import java.io.Writer
import java.time.OffsetDateTime
import java.util.Locale
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Le letture metrologiche sui dati di una MU: lo stato di taratura dei suoi sensori e
 * l'esportazione dei campioni.
 */
@Service
class SensorDataService(
        private val mur: MeasurementUnitRepository,
        private val samples: MetricSampleRepository,
        private val calibrations: ActiveCalibrationService,
        private val templates: TemplateService,
        private val ss: SecurityService,
) {

    /** Lo stato di taratura di ogni sensore della MU, adesso. */
    @Transactional(readOnly = true)
    fun calibrationStatus(muId: Long): List<SensorCalibrationStatusDTO> {
        val mu = owned(muId)
        val now = OffsetDateTime.now()
        return mu.sensors.sortedBy { it.sensorIndex }.map { sensor ->
            val history = calibrations.history(mu.extendedId, sensor.sensorIndex)
            val resolved = calibrations.resolve(mu.extendedId, sensor.sensorIndex, now)
            val spec = MetrologySpec.of(templates.getTemplate(sensor.modelName)?.metrology)
            val last =
                    samples.findTopBySensor_IdAndMetricInOrderByTimestampDesc(
                            sensor.id,
                            listOf(Metric.MEAN, Metric.PUNCTUAL),
                    )
            SensorCalibrationStatusDTO(
                    sensorId = sensor.id,
                    sensorIndex = sensor.sensorIndex,
                    modelName = sensor.modelName,
                    channel = sensor.channel,
                    requiresCalibration = spec.requiresCalibration,
                    state =
                            when {
                                resolved != null && resolved.expired -> "EXPIRED"
                                resolved != null -> "INSTANCE"
                                !spec.requiresCalibration -> "NOT_REQUIRED"
                                else -> "NOMINAL"
                            },
                    current = resolved?.record?.toDTO(),
                    history = history.map { it.toDTO() },
                    lastSample = last?.toQualityDTO(),
            )
        }
    }

    /**
     * I campioni di un sensore in un intervallo, in CSV (PIANO_METROLOGIA 2.8). Una riga per
     * metrica, con tutto cio' che serve a rifare un grafico o un controllo: valore registrato,
     * grezzo, incertezza, flag, taratura e versione del template.
     *
     * Separatore `;` e punto decimale: e' il formato che Excel in italiano apre senza
     * chiedere niente, e che pandas legge con `sep=";"`.
     */
    @Transactional(readOnly = true)
    fun exportCsv(muId: Long, sensorId: Long, from: OffsetDateTime, to: OffsetDateTime, out: Writer) {
        val mu = owned(muId)
        val sensor =
                mu.sensors.firstOrNull { it.id == sensorId }
                        ?: throw EntityNotFoundException("Sensore $sensorId non trovato sulla MU $muId")

        out.write(
                "timestamp;window_start;window_end;metric;value;raw_value;converted;u_std;U_k2;" +
                        "quality_flags;flag_names;certificate_id;certificate_number;template_version;" +
                        "mu_extended_id;sensor_index;model\n"
        )
        samples.findBySensor_IdAndTimestampBetweenOrderByTimestampAscMetricAsc(sensor.id, from, to).forEach { s ->
            val cells =
                    listOf(
                            s.timestamp.toString(),
                            s.windowStart?.toString(),
                            s.windowEnd?.toString(),
                            s.metric,
                            number(s.value),
                            number(s.rawValue),
                            s.converted?.toString(),
                            number(s.uStd),
                            number(s.uStd?.let { 2 * it }),
                            s.qualityFlags?.toString(),
                            QualityFlags.names(s.qualityFlags).joinToString("|"),
                            s.calibration?.certificateId?.toString(),
                            s.calibration?.certificateNumber,
                            s.templateVersion,
                            mu.extendedId.toString(),
                            sensor.sensorIndex.toString(),
                            sensor.modelName,
                    )
            out.write(cells.joinToString(";") { it?.replace(";", ",") ?: "" })
            out.write("\n")
        }
        out.flush()
    }

    private fun number(value: Double?): String? = value?.let { String.format(Locale.ROOT, "%.10g", it) }

    private fun owned(id: Long): MeasurementUnit =
            (if (ss.isAdmin()) mur.findByIdOrNull(id) else mur.findByIdAndUser_UserId(id, ss.getCurrentUserId()))
                    ?: throw EntityNotFoundException("Measurement Unit $id non trovata")
}
