package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.UserDTO

interface UserService {
    fun list():List<UserDTO>
}