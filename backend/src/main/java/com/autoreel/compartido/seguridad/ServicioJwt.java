package com.autoreel.compartido.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.autoreel.auth.dominio.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Emite y lee el JWT de la sesión (HS256). El token lleva el id del usuario y su versión de sesión;
 * los permisos se toman siempre de la base de datos en cada petición.
 */
@Component
public class ServicioJwt {

    static final String CLAIM_VERSION = "ver";
    /** Negocio en el que entró el administrador. */
    static final String CLAIM_NEGOCIO = "neg";
    private static final int BYTES_MINIMOS_CLAVE = 32;

    private final JwtEncoder codificador;
    private final JwtDecoder decodificador;
    private final Duration duracion;
    private final Clock reloj;

    public ServicioJwt(PropiedadesSeguridad propiedades, Clock reloj) {
        SecretKey clave = claveDesde(propiedades.jwtSecreto());
        this.codificador = NimbusJwtEncoder.withSecretKey(clave).algorithm(MacAlgorithm.HS256).build();
        this.decodificador = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        this.duracion = Duration.ofHours(propiedades.sesionHoras());
        this.reloj = reloj;
    }

    public String emitir(Usuario usuario) {
        return emitir(usuario, null);
    }

    /**
     * Sesión del administrador que entra como un negocio: sigue siendo su token (su id y su versión de sesión), con
     * el negocio en el que entró. Si el administrador cambia su contraseña, también se cierra esta sesión.
     */
    public String emitirComoNegocio(Usuario administrador, Long negocioId) {
        return emitir(administrador, negocioId);
    }

    private String emitir(Usuario usuario, Long negocioId) {
        Instant ahora = reloj.instant();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer("autoreel")
                .subject(usuario.getId().toString())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracion))
                .claim(CLAIM_VERSION, usuario.getVersionSesion());
        if (negocioId != null) {
            builder.claim(CLAIM_NEGOCIO, negocioId);
        }
        JwtClaimsSet claims = builder.build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    /**
     * @throws JwtException si el token es inválido, está mal firmado o venció.
     */
    public Jwt leer(String token) {
        return decodificador.decode(token);
    }

    public Duration getDuracion() {
        return duracion;
    }

    private static SecretKey claveDesde(String secretoBase64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secretoBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET debe estar en base64 (genéralo con: openssl rand -base64 32).");
        }
        if (bytes.length < BYTES_MINIMOS_CLAVE) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + BYTES_MINIMOS_CLAVE
                    + " bytes (genéralo con: openssl rand -base64 32).");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
