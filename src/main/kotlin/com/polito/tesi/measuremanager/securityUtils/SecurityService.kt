package com.polito.tesi.measuremanager.securityUtils

import org.springframework.stereotype.Component
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.core.context.SecurityContextHolder
import com.polito.tesi.measuremanager.entities.User
import com.polito.tesi.measuremanager.repositories.UserRepository

@Component
class SecurityService(private val userRepository: UserRepository) {

    private fun getJwt(): Jwt {
        val auth = SecurityContextHolder.getContext().authentication
        return auth.principal as? Jwt ?: throw IllegalStateException("JWT non trovato nel contesto di sicurezza")
    }

    fun getCurrentUserId(): String = getJwt().subject

    fun isAdmin(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
        return auth.authorities.any { it.authority == "ROLE_app-admin" }
    }

    fun getCurrentUserInfo(): Map<String, String?> {
        val jwt = getJwt()
        return mapOf(
            "userId" to jwt.subject,
            "email" to jwt.getClaim<String>("email"),
            "givenName" to jwt.getClaim<String>("given_name"),
            "familyName" to jwt.getClaim<String>("family_name")
        )
    }

    fun getOrCreateCurrentUser(): User {
        val userId = getCurrentUserId()
        return userRepository.findById(userId).orElseGet {
            val info = getCurrentUserInfo()
            userRepository.save(User().apply {
                this.userId = userId
                this.name = info["givenName"] ?: ""
                this.surname = info["familyName"] ?: ""
                this.email = info["email"] ?: ""
            })
        }
    }
}
