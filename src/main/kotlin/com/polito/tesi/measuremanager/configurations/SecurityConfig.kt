package com.polito.tesi.measuremanager.configurations

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
    @Bean
    fun filterChain(httpSecurity: HttpSecurity):SecurityFilterChain {
        return httpSecurity
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/**").permitAll()
                it.anyRequest().authenticated()

            }
            .oauth2ResourceServer { it.jwt {}  }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)}
            .csrf{it.disable()}
            .cors { it.disable() }
            .build()
    }
    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val jwtAuthenticationConverter = JwtAuthenticationConverter()
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(CustomJwtGrantedAuthoritiesConverter())
        return jwtAuthenticationConverter
    }
}

class CustomJwtGrantedAuthoritiesConverter : Converter<Jwt, Collection<GrantedAuthority>> {

    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        val authorities = mutableListOf<GrantedAuthority>()

        // Estrai le autorità da resource_access.iam1client.roles
        val resourceAccess = jwt.claims["resource_access"] as? Map<*, *>
        val iamClientAccess = resourceAccess?.get("iam1client") as? Map<*, *>
        val iamClientRoles = iamClientAccess?.get("roles") as? List<*>

        iamClientRoles?.forEach {
            if (it is String) {
                authorities.add(SimpleGrantedAuthority("ROLE_$it"))
            }
        }
        val realmAccess = jwt.claims["realm_access"] as? Map<*, *>
        val iamClientRoles1 = realmAccess?.get("roles") as? List<*>
        iamClientRoles1?.forEach {
            if (it is String) {
                authorities.add(SimpleGrantedAuthority("ROLE_$it"))
            }
        }
        return authorities
    }
}