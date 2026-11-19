package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.SensorCalibrationStatusDTO
import com.polito.tesi.measuremanager.services.SensorDataService
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Le letture metrologiche di una MU. Stanno sotto `/API/measurementunits` perche' il gateway
 * instrada gia' quel prefisso, e perche' i permessi sono quelli della MU: chi la vede, vede
 * anche la taratura dei suoi sensori.
 */
@RestController
@RequestMapping("/API/measurementunits")
class SensorDataController(private val service: SensorDataService) {

    /** Con quale taratura si stanno convertendo adesso le misure di ogni sensore. */
    @GetMapping("/{id}/calibrations")
    fun calibrations(@PathVariable id: Long): List<SensorCalibrationStatusDTO> = service.calibrationStatus(id)

    /** I campioni di un sensore in CSV. Senza estremi: gli ultimi trenta giorni. */
    @GetMapping("/{id}/sensors/{sensorId}/export.csv")
    fun export(
            @PathVariable id: Long,
            @PathVariable sensorId: Long,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: OffsetDateTime?,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: OffsetDateTime?,
            response: HttpServletResponse,
    ) {
        val end = to ?: OffsetDateTime.now()
        val start = from ?: end.minusDays(30)
        response.contentType = "text/csv; charset=UTF-8"
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.setHeader("Content-Disposition", "attachment; filename=\"mu-$id-sensor-$sensorId.csv\"")
        service.exportCsv(id, sensorId, start, end, response.writer)
    }
}
