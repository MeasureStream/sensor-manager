package com.polito.tesi.measuremanager.securityUtils

import org.springframework.stereotype.Component
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.core.context.SecurityContextHolder
import com.polito.tesi.measuremanager.entities.User
import com.polito.tesi.measuremanager.repositories.UserRepository

@Component
class SecurityService(private val userRepository: UserRepository) {
    companion object {
        /** Amministratore della piattaforma: legge e scrive tutto. */
        const val ADMIN = "app-admin"

        /** Personale del laboratorio: legge l'anagrafica di tutte le MU, senza scrivere. */
        const val STAFF = "app-staff"
    }

    private fun getJwt(): Jwt {
        val auth = SecurityContextHolder.getContext().authentication
        return auth.principal as? Jwt ?: throw IllegalStateException("JWT non trovato nel contesto di sicurezza")
    }

    fun getCurrentUserId(): String = getJwt().subject

    private fun hasRealmRole(role: String): Boolean =
        SecurityContextHolder.getContext().authentication?.authorities?.any { it.authority == "ROLE_$role" } ?: false

    fun isAdmin(): Boolean = hasRealmRole(ADMIN)

    /**
     * Personale del laboratorio interno, ruolo di realm `app-staff`: tara e verifica anche le MU
     * dei clienti, quindi ne legge l'anagrafica (elenco, dettaglio con i sensori, eventi). Non
     * scrive niente e non legge le misure, che restano al proprietario e all'amministratore. Che
     * cosa possa fare in una taratura lo dicono i ruoli metrologici di metrology-service, che
     * hanno una validita' nel tempo: questo e' solo un ruolo di accesso.
     */
    fun isStaff(): Boolean = hasRealmRole(STAFF)

    /** Puo' leggere l'anagrafica di tutte le MU: l'amministratore e il personale del laboratorio. */
    fun canReadAllUnits(): Boolean = isAdmin() || isStaff()

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
