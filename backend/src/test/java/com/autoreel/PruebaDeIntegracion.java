package com.autoreel;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.autoreel.compartido.config.ConfiguracionReloj;
import com.autoreel.compartido.seguridad.CookieDeSesion;
import com.autoreel.facturacion.infraestructura.PlanRepositorio;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Base de las pruebas de integración: aplicación completa, PostgreSQL real (Testcontainers) con las migraciones
 * de Flyway, y un reloj ajustable para probar vencimientos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PruebaDeIntegracion.RelojDePrueba.class})
public abstract class PruebaDeIntegracion {

    protected static final String CORREO_ADMIN = "admin@autoreel.test";
    protected static final String CONTRASENA_ADMIN = "AdminPrueba123";
    private static final AtomicInteger contador = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected RelojAjustable reloj;

    @Autowired
    protected PlanRepositorio planes;

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojDePrueba {

        @Bean
        @Primary
        RelojAjustable relojAjustable() {
            return new RelojAjustable(ConfiguracionReloj.ZONA_LIMA);
        }
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    protected static String correoUnico() {
        return "negocio" + contador.incrementAndGet() + "-" + System.nanoTime() + "@autoreel.test";
    }

    protected ResultActions iniciarSesion(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo, "contrasena", contrasena))));
    }

    protected Cookie sesion(String correo, String contrasena) throws Exception {
        MvcResult resultado = iniciarSesion(correo, contrasena).andExpect(status().isOk()).andReturn();
        return resultado.getResponse().getCookie(CookieDeSesion.NOMBRE);
    }

    protected Cookie sesionAdmin() throws Exception {
        return sesion(CORREO_ADMIN, CONTRASENA_ADMIN);
    }

    protected Long planLite() {
        return planes.findAll().stream().filter(plan -> plan.getNombre().equals("Lite")).findFirst().orElseThrow()
                .getId();
    }

    protected Map<String, Object> solicitudNegocio(String correo) {
        return Map.of("nicho", "PASTELERIA", "nombre", "Pastelería Dulce Encanto", "correo", correo,
                "planId", planLite(), "montoMensual", new BigDecimal("49.00"),
                "fechaPrimerPago", LocalDate.now(reloj).plusDays(30).toString());
    }

    protected ResultActions crearNegocio(Cookie admin, Map<String, Object> solicitud) throws Exception {
        return mvc.perform(post("/api/admin/negocios").with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(solicitud)));
    }

    /** Crea un negocio y devuelve la respuesta con su contraseña temporal. */
    protected JsonNode negocioCreado(String correo) throws Exception {
        MvcResult resultado = crearNegocio(sesionAdmin(), solicitudNegocio(correo))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(resultado.getResponse().getContentAsString());
    }

    /** Crea un negocio, entra con la contraseña temporal, define una propia y devuelve la sesión ya válida. */
    protected Cookie sesionNegocioListo(String correo, String contrasenaPropia) throws Exception {
        String temporal = negocioCreado(correo).get("contrasenaTemporal").asString();
        Cookie temporalCookie = sesion(correo, temporal);
        MvcResult resultado = mvc.perform(post("/api/auth/cambiar-contrasena").with(csrf()).cookie(temporalCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nueva", contrasenaPropia,
                                "confirmacion", contrasenaPropia))))
                .andExpect(status().isOk()).andReturn();
        return resultado.getResponse().getCookie(CookieDeSesion.NOMBRE);
    }
}
