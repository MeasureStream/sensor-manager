package com.polito.tesi.measuremanager.dtos

import com.polito.tesi.measuremanager.entities.User

data class UserDTO(val userId: String, val email:String, val name:String,val surname:String )

fun User.toDTO() = UserDTO(userId,email,name,surname)