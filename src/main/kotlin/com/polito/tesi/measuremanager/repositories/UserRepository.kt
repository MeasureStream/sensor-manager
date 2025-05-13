package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository:JpaRepository<User,String> {
}