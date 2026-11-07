package com.autoreel.compartido.seguridad;

import com.autoreel.auth.infraestructura.UsuarioRepositorio;
import com.autoreel.negocio.infraestructura.NegocioRepositorio;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Sesión sin estado en el servidor: un JWT en cookie HttpOnly (RNF-04), CSRF para SPA y permisos por rol
 * verificados en el backend (RN-06). Ver ADR 0007.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ServicioJwt servicioJwt, CookieDeSesion cookieDeSesion,
            UsuarioRepositorio usuarios, NegocioRepositorio negocios, RespuestasDeSeguridad respuestas)
            throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .addFilterAfter(new FiltroCookieCsrf(), CsrfFilter.class)
                .addFilterBefore(new FiltroJwtCookie(servicioJwt, cookieDeSesion, usuarios, negocios),
                        AuthorizationFilter.class)
                .authorizeHttpRequests(acceso -> acceso
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/logout", "/actuator/health").permitAll()
                        // Solo se publican en el perfil dev (springdoc está desactivado en los demás).
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Con contraseña temporal solo se puede consultar la sesión y cambiarla (RN-03).
                        .requestMatchers("/api/auth/yo", "/api/auth/cambiar-contrasena").authenticated()
                        // Solo para el administrador que entró como un negocio (lo verifica el controlador).
                        .requestMatchers("/api/auth/volver-al-panel").hasRole("NEGOCIO")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasRole("NEGOCIO")
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    PasswordEncoder codificadorDeContrasenas() {
        return new BCryptPasswordEncoder();
    }
}
