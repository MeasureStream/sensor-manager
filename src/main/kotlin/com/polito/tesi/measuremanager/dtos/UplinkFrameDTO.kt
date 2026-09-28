package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.FrameStatus
import com.polito.tesi.measuremanager.entities.UplinkFrame
import java.time.OffsetDateTime

/**
 * Un frame ricevuto, come lo vede la scheda diagnostica.
 *
 * Il payload grezzo non viaggia verso l'interfaccia: serve a rileggere il report lato
 * server, non a essere mostrato. Qui basta sapere quando e' arrivato, com'e' andata e
 * perche' e' stato scartato.
 */
data class UplinkFrameDTO(
        val id: Long,
        val receivedAt: OffsetDateTime,
        val status: FrameStatus,
        val cfgVersion: Int?,
        val expectedCfgVersion: Int?,
        val failureReason: String?,
        val acknowledged: Boolean,
)

fun UplinkFrame.toDTO() =
        UplinkFrameDTO(
                id = id,
                receivedAt = receivedAt,
                status = status,
                cfgVersion = cfgVersion,
                expectedCfgVersion = expectedCfgVersion,
                failureReason = failureReason,
                acknowledged = acknowledgedAt != null,
        )
