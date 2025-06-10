package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.dtos.UserDTO
import com.polito.tesi.measuremanager.services.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/API/user")
class UserController(private val us: UserService) {
    @GetMapping("/","")
    fun list() :List<UserDTO>{
        return us.list()
    }
}