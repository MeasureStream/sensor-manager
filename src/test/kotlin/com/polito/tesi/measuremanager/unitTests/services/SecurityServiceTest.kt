package com.polito.tesi.measuremanager.unitTests.services

import com.polito.tesi.measuremanager.repositories.UserRepository
import com.polito.tesi.measuremanager.securityUtils.SecurityService
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** I ruoli di realm che sensor-manager riconosce: chi legge tutte le MU e chi solo le proprie. */
class SecurityServiceTest {
    private val ss = SecurityService(mockk<UserRepository>())

    private fun login(vararg roles: String) {
        val jwt =
            Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("utente")
                .claim("realm_access", mapOf("roles" to roles.toList()))
                .build()
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwt, roles.map { SimpleGrantedAuthority("ROLE_$it") })
    }

    @AfterEach
    fun logout() = SecurityContextHolder.clearContext()

    @Test
    fun `l'amministratore legge tutte le MU`() {
        login(SecurityService.ADMIN)
        assertTrue(ss.isAdmin())
        assertTrue(ss.canReadAllUnits())
    }

    @Test
    fun `il personale del laboratorio legge tutte le MU ma non e' amministratore`() {
        login(SecurityService.STAFF, "app-user")
        assertFalse(ss.isAdmin())
        assertTrue(ss.isStaff())
        assertTrue(ss.canReadAllUnits())
    }

    @Test
    fun `un cliente e un laboratorio esterno leggono solo le proprie`() {
        login("app-user")
        assertFalse(ss.canReadAllUnits())
        login("app-lab")
        assertFalse(ss.canReadAllUnits())
    }

    @Test
    fun `senza autenticazione nessun ruolo`() {
        SecurityContextHolder.clearContext()
        assertFalse(ss.isAdmin())
        assertFalse(ss.canReadAllUnits())
    }
}
