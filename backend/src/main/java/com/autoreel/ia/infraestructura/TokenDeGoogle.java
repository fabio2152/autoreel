package com.autoreel.ia.infraestructura;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.ErrorDeProveedor.Tipo;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Token de acceso de Google Cloud a partir de la clave de una cuenta de servicio (flujo JWT bearer): firma un JWT
 * RS256 con la clave privada y lo cambia por un token de una hora. Guarda un token por cuenta y clave hasta un
 * minuto antes de vencer. Ningún error incluye la clave.
 */
public class TokenDeGoogle {

    static final String ALCANCE = "https://www.googleapis.com/auth/cloud-platform";
    private static final Duration MARGEN = Duration.ofMinutes(1);

    /** Lo que se usa del JSON de la cuenta de servicio. */
    public record CuentaDeServicio(String proyecto, String correo, String idClave, RSAPrivateKey clave) {
    }

    private record TokenGuardado(String valor, Instant vence) {
    }

    private final RestClient cliente;
    private final String urlToken;
    private final ObjectMapper json;
    private final Clock reloj;
    private final Map<String, TokenGuardado> tokens = new ConcurrentHashMap<>();

    public TokenDeGoogle(RestClient cliente, String urlToken, ObjectMapper json, Clock reloj) {
        this.cliente = cliente;
        this.urlToken = urlToken;
        this.json = json;
        this.reloj = reloj;
    }

    /** Lee el JSON de la cuenta de servicio. */
    public CuentaDeServicio cuenta(String credencial) {
        try {
            JsonNode nodo = json.readTree(credencial);
            String proyecto = texto(nodo, "project_id");
            String correo = texto(nodo, "client_email");
            String pem = texto(nodo, "private_key");
            String der = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            RSAPrivateKey clave = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(der)));
            return new CuentaDeServicio(proyecto, correo, nodo.path("private_key_id").asString(""), clave);
        } catch (JacksonException | IllegalArgumentException | java.security.GeneralSecurityException
                | ClassCastException e) {
            throw new ErrorDeProveedor(Tipo.CREDENCIAL_INVALIDA,
                    "La credencial de Google Cloud no es el JSON de una cuenta de servicio válido");
        }
    }

    public String token(CuentaDeServicio cuenta) {
        String llave = cuenta.correo() + "#" + cuenta.idClave();
        Instant ahora = reloj.instant();
        TokenGuardado guardado = tokens.get(llave);
        if (guardado != null && guardado.vence().isAfter(ahora.plus(MARGEN))) {
            return guardado.valor();
        }
        TokenGuardado nuevo = pedir(cuenta, ahora);
        tokens.put(llave, nuevo);
        return nuevo.valor();
    }

    /** Ante un 401 del proveedor: el token pudo revocarse. */
    public void olvidar(CuentaDeServicio cuenta) {
        tokens.remove(cuenta.correo() + "#" + cuenta.idClave());
    }

    private TokenGuardado pedir(CuentaDeServicio cuenta, Instant ahora) {
        try {
            JWTClaimsSet afirmaciones = new JWTClaimsSet.Builder()
                    .issuer(cuenta.correo())
                    .audience(urlToken)
                    .claim("scope", ALCANCE)
                    .issueTime(Date.from(ahora))
                    .expirationTime(Date.from(ahora.plus(Duration.ofHours(1))))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(cuenta.idClave()).build(),
                    afirmaciones);
            jwt.sign(new RSASSASigner(cuenta.clave()));
            var formulario = new LinkedMultiValueMap<String, String>();
            formulario.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            formulario.add("assertion", jwt.serialize());
            JsonNode respuesta = cliente.post().uri(urlToken).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario).retrieve().body(JsonNode.class);
            String token = respuesta == null ? "" : respuesta.path("access_token").asString("");
            if (token.isEmpty()) {
                throw new ErrorDeProveedor(Tipo.CREDENCIAL_INVALIDA, "Google no entregó un token de acceso");
            }
            long segundos = respuesta.path("expires_in").asLong(3600);
            return new TokenGuardado(token, ahora.plusSeconds(segundos));
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new ErrorDeProveedor(Tipo.CREDENCIAL_INVALIDA, "No se pudo firmar con la cuenta de servicio", e);
        } catch (RestClientException e) {
            throw new ErrorDeProveedor(Tipo.CREDENCIAL_INVALIDA,
                    "Google rechazó la cuenta de servicio o no respondió al pedir el token", e);
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        String valor = nodo.path(campo).asString("");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + campo);
        }
        return valor;
    }
}
