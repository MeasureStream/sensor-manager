package com.polito.tesi.measuremanager.services

import com.polito.tesi.measuremanager.dtos.UserDTO
import com.polito.tesi.measuremanager.dtos.toDTO
import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.repositories.UserRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class UserServiceImpl(private val ur: UserRepository):UserService {
    override fun list(): List<UserDTO> {
        if(!isAdmin()) throw OperationNotAllowed("Operation not Allowed")
        return ur.findAll().map { it.toDTO() }
    }

    fun isAdmin() : Boolean{
        val auth = SecurityContextHolder.getContext().authentication
        val isAdmin = auth.authorities.any { it.authority == "ROLE_app-admin" }
        return isAdmin
    }

}