package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.Node
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.data.geo.Point


data class NodeDTO(
    @NotNull
    val id: Long,
    @NotBlank
    val name: String,
    @NotNull
    val standard :  Boolean,
    val controlUnitsId :  Set<Long>,
    val measurementUnitsId :  Set<Long>,
    @NotNull
    val location: Point,


)

fun Node.toDTO() = NodeDTO(id,name,standard, this.controlUnits.map { e-> e.id }.toSet(),this.measurementUnits.map { e-> e.id }.toSet() , this.location )



