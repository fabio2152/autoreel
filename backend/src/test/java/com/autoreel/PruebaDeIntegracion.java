package com.autoreel;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    @Autowired
    protected AlmacenEnMemoria almacen;

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojDePrueba {

        @Bean
        @Primary
        RelojAjustable relojAjustable() {
            return new RelojAjustable(ConfiguracionReloj.ZONA_LIMA);
        }

        @Bean
        AlmacenEnMemoria almacenEnMemoria() {
            return new AlmacenEnMemoria();
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

    protected ResultActions pedirSubida(Cookie cookie, String proposito, String tipo, long tamano) throws Exception {
        return mvc.perform(post("/api/archivos/url-subida").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("proposito", proposito, "tipoContenido", tipo,
                        "tamano", tamano))));
    }

    /** Pide la URL firmada y "sube" el archivo, como haría el navegador. Devuelve la clave temporal. */
    protected String subir(Cookie cookie, String proposito, byte[] contenido, String tipo) throws Exception {
        String clave = json.readTree(pedirSubida(cookie, proposito, tipo, contenido.length)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("clave").asString();
        almacen.guardar(clave, contenido, tipo);
        return clave;
    }

    protected Map<String, String> fotosCompletas(Cookie cookie) throws Exception {
        return Map.of(
                "ENTERO", subir(cookie, "FOTO_PRODUCTO", Imagenes.jpg(1080, 1920), "image/jpeg"),
                "DETALLE", subir(cookie, "FOTO_PRODUCTO", Imagenes.jpg(1080, 1920), "image/jpeg"),
                "PORCION", subir(cookie, "FOTO_PRODUCTO", Imagenes.jpg(1080, 1920), "image/jpeg"));
    }

    /** Negocio con contraseña propia y onboarding completo (RN-09), listo para crear videos. */
    protected Cookie sesionNegocioConOnboarding(String correo) throws Exception {
        Cookie cookie = sesionNegocioListo(correo, "MiClave2026");
        Map<String, Object> perfil = Map.of(
                "logo", subir(cookie, "LOGO", Imagenes.png(500, 500, true), "image/png"),
                "colorPrimario", "#be123c", "colorSecundario", "#fde68a", "estilo", "CASERO",
                "respuestas", Map.of("especialidad", List.of("TORTAS_PERSONALIZADAS"), "publico", List.of("FAMILIAS"),
                        "ciudad", "Lima", "distrito", "Miraflores", "canales", List.of("TIENDA"),
                        "horario", "Lunes a sábado de 9 a 8"));
        mvc.perform(put("/api/perfil").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(perfil))).andExpect(status().isOk());
        mvc.perform(post("/api/perfil/completar-onboarding").with(csrf()).cookie(cookie)).andExpect(status().isOk());
        return cookie;
    }
}
