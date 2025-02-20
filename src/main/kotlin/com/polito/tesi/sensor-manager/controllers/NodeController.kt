package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.NodeDTO
import com.polito.tesi.measuremanager.services.NodeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/API/nodes")
class NodeController ( private val ns: NodeService) {

    @GetMapping("/","")
    fun get(@RequestParam id: Long?, @RequestParam name: String? ): List<NodeDTO> {
        id?.let { return listOf( ns.getNode(id) ) }
        return ns.getAllNodes( name = name)
    }
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/","")
    fun createN(@Valid @RequestBody n : NodeDTO): NodeDTO {
        return ns.create(n)
    }
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PutMapping("/","")
    fun updateN(@Valid @RequestBody n : NodeDTO): NodeDTO {
        return ns.update(n.id,n)
    }
    @ResponseStatus(HttpStatus.ACCEPTED)
    @DeleteMapping("/","")
    fun deleteN(@Valid @RequestBody n : NodeDTO){
        return ns.delete(n.id)
    }



}