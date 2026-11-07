package com.autoreel.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.autoreel.PruebaDeIntegracion;
import com.autoreel.compartido.seguridad.CookieDeSesion;
import com.autoreel.facturacion.aplicacion.ResolutorDePlanEfectivo;
import com.autoreel.facturacion.aplicacion.ServicioNotificaciones;
import com.autoreel.facturacion.dominio.NotificacionAdmin;
import com.autoreel.facturacion.dominio.TipoNotificacion;
import com.autoreel.facturacion.infraestructura.NotificacionRepositorio;
import com.autoreel.ia.infraestructura.ModeloIARepositorio;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Fase 7: pagos, notificaciones, planes, integraciones, beneficios, costos y entrar como el negocio. */
class FacturacionIntegracionTest extends PruebaDeIntegracion {

    private static final String CUENTA_DE_SERVICIO = "{\"type\":\"service_account\","
            + "\"client_email\":\"autoreel@proyecto-prueba.iam.gserviceaccount.com\"}";

    @Autowired
    ServicioNotificaciones notificaciones;

    @Autowired
    NotificacionRepositorio repositorioNotificaciones;

    @Autowired
    ResolutorDePlanEfectivo planesEfectivos;

    @Autowired
    ModeloIARepositorio modelos;

    @AfterEach
    void borrarCredencialesDelPanel() throws Exception {
        Files.deleteIfExists(Path.of("target/secretos-del-panel-prueba.json"));
    }

    @Test
    void v34LasNotificacionesDePagoAparecenUnaVezPorEstado() throws Exception {
        Long negocio = crearNegocioConPrimerPago(LocalDate.now(reloj).plusDays(5));

        notificaciones.generarDePagos();
        notificaciones.generarDePagos();
        assertThat(tipos(negocio)).containsExactly(TipoNotificacion.PAGO_POR_VENCER);

        reloj.avanzar(Duration.ofDays(5));
        notificaciones.generarDePagos();
        reloj.avanzar(Duration.ofDays(1));
        notificaciones.generarDePagos();
        notificaciones.generarDePagos();
        assertThat(tipos(negocio)).containsExactlyInAnyOrder(TipoNotificacion.PAGO_POR_VENCER,
                TipoNotificacion.PAGO_HOY, TipoNotificacion.PAGO_VENCIDO);
    }

    @Test
    void v36ALosSieteDiasDeVencidoSeSugiereSuspenderYElNegocioSigueActivo() throws Exception {
        Long negocio = crearNegocioConPrimerPago(LocalDate.now(reloj));
        reloj.avanzar(Duration.ofDays(6));
        notificaciones.generarDePagos();
        assertThat(tipos(negocio)).doesNotContain(TipoNotificacion.SUGERENCIA_SUSPENSION);

        reloj.avanzar(Duration.ofDays(1));
        notificaciones.generarDePagos();
        assertThat(tipos(negocio)).contains(TipoNotificacion.SUGERENCIA_SUSPENSION);
        mvc.perform(get("/api/admin/negocios/" + negocio).cookie(sesionAdmin()))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.suscripcion.estadoPago").value("VENCIDO"));
    }

    @Test
    void v35RegistrarElPagoAvanzaLaFechaUnMesYAtiendeSusNotificaciones() throws Exception {
        LocalDate fecha = LocalDate.now(reloj).plusDays(2);
        Long negocio = crearNegocioConPrimerPago(fecha);
        notificaciones.generarDePagos();
        Cookie admin = sesionAdmin();

        mvc.perform(post("/api/admin/negocios/" + negocio + "/pagos").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fechaPago", LocalDate.now(reloj).toString(),
                                "monto", 49, "medio", "YAPE", "nota", "Pagó por Yape"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.periodoCubierto").value(fecha.toString()))
                .andExpect(jsonPath("$.proximaFechaPago").value(fecha.plusMonths(1).toString()));

        mvc.perform(get("/api/admin/negocios/" + negocio + "/pagos").cookie(admin))
                .andExpect(jsonPath("$[0].medio").value("YAPE"))
                .andExpect(jsonPath("$[0].monto").value(49.0));
        assertThat(repositorioNotificaciones.findAll().stream().filter(n -> negocio.equals(n.getNegocioId())))
                .isNotEmpty().allMatch(NotificacionAdmin::isLeida);
    }

    @Test
    void unPagoConFechaFuturaOSinMedioSeRechaza() throws Exception {
        Long negocio = crearNegocioConPrimerPago(LocalDate.now(reloj).plusDays(10));
        Cookie admin = sesionAdmin();
        registrarPago(admin, negocio, Map.of("fechaPago", LocalDate.now(reloj).plusDays(1).toString(), "monto", 49,
                "medio", "YAPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PAGO_FECHA_FUTURA"));
        registrarPago(admin, negocio, Map.of("fechaPago", LocalDate.now(reloj).toString(), "monto", 49))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Elige el medio de pago."));
    }

    @Test
    void v37IntegracionesMuestraLosModelosYProbarConexionInformaSiFaltaLaCredencial() throws Exception {
        Cookie admin = sesionAdmin();
        JsonNode integraciones = json.readTree(mvc.perform(get("/api/admin/integraciones").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adaptadores.VIDEO").isArray())
                .andReturn().getResponse().getContentAsString());
        List<String> nombres = new java.util.ArrayList<>();
        integraciones.get("modelos").forEach(modelo -> nombres.add(modelo.get("nombre").asString()));
        assertThat(nombres).contains("Gemini", "Gemini Omni Flash", "Veo 3.1 Lite");

        mvc.perform(post("/api/admin/modelos-ia/" + modelo("GEMINI_OMNI") + "/probar").with(csrf()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitosa").value(false))
                .andExpect(jsonPath("$.mensaje").value("No hay credencial configurada para autoreel/gemini."));
    }

    @Test
    void v38NoSePuedeDesactivarUnModeloQueUsaUnPlanActivo() throws Exception {
        mvc.perform(post("/api/admin/modelos-ia/" + modelo("GEMINI_OMNI") + "/desactivar").with(csrf())
                        .cookie(sesionAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MODELO_EN_USO"));
    }

    @Test
    void laCredencialSePegaEnElPanelYNuncaSeVuelveAMostrar() throws Exception {
        Cookie admin = sesionAdmin();
        String clave = "AIzaClaveDePrueba-1234abcd";
        String respuesta = mvc.perform(put("/api/admin/credenciales").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombre", "autoreel/gemini", "valor", clave))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("PANEL"))
                .andExpect(jsonPath("$.descripcion").value("Termina en …abcd"))
                .andReturn().getResponse().getContentAsString();
        String listado = mvc.perform(get("/api/admin/integraciones").cookie(admin))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta + listado).doesNotContain(clave);

        mvc.perform(delete("/api/admin/credenciales").param("nombre", "autoreel/gemini").with(csrf()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("NINGUNO"));
        mvc.perform(put("/api/admin/credenciales").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombre", "autoreel/otra", "valor", clave))))
                .andExpect(status().isNotFound());
    }

    /** V40 y V41: un plan Pro de prueba con otro modelo de video, dado como beneficio por un día. */
    @Test
    void v40ElBeneficioCambiaElModeloDeVideoYAlTerminarVuelveAlPlanLite() throws Exception {
        Cookie admin = sesionAdmin();
        Long veo = modelo("VEO");
        String nombrePlan = "Pro " + System.nanoTime() % 100000;
        Long planPro = null;
        try {
            guardarCredencial(admin, "autoreel/google-cloud", CUENTA_DE_SERVICIO);
            mvc.perform(post("/api/admin/modelos-ia/" + veo + "/activar").with(csrf()).cookie(admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVO"));
            planPro = json.readTree(mvc.perform(post("/api/admin/planes").with(csrf()).cookie(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(plan(nombrePlan, veo, true))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.modeloVideo").value("Veo 3.1 Lite"))
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();

            Long negocio = crearNegocioConPrimerPago(LocalDate.now(reloj).plusDays(20));
            Instant ahora = reloj.instant();
            String ruta = "/api/admin/negocios/" + negocio + "/beneficios";
            otorgar(admin, ruta, planPro, ahora, ahora.plus(Duration.ofDays(31)), "Cortesía")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("BENEFICIO_MUY_LARGO"));
            otorgar(admin, ruta, planPro, ahora, ahora.plus(Duration.ofDays(1)), " ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("Escribe el motivo del beneficio."));
            otorgar(admin, ruta, planPro, ahora, ahora.plus(Duration.ofDays(1)), "Por el inconveniente de ayer")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("VIGENTE"));
            otorgar(admin, ruta, planPro, ahora.plus(Duration.ofHours(2)), ahora.plus(Duration.ofDays(2)), "Otro")
                    .andExpect(status().isConflict());

            assertThat(planesEfectivos.de(negocio).getModeloVideo().getNombre()).isEqualTo("Veo 3.1 Lite");
            mvc.perform(get("/api/admin/negocios/" + negocio).cookie(admin))
                    .andExpect(jsonPath("$.planEfectivo.plan").value(nombrePlan))
                    .andExpect(jsonPath("$.planEfectivo.modeloVideo").value("Veo 3.1 Lite"))
                    .andExpect(jsonPath("$.suscripcion.plan").value("Lite"));

            reloj.avanzar(Duration.ofDays(1).plusMinutes(1));
            assertThat(planesEfectivos.de(negocio).getModeloVideo().getNombre()).isEqualTo("Gemini Omni Flash");
        } finally {
            reloj.reiniciar();
            if (planPro != null) {
                mvc.perform(put("/api/admin/planes/" + planPro).with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(plan(nombrePlan, veo, false))));
            }
            mvc.perform(post("/api/admin/modelos-ia/" + veo + "/desactivar").with(csrf()).cookie(admin))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void cambiarElPlanDelNegocioRigeParaLoQueVieneYPuedeCambiarElMonto() throws Exception {
        Long negocio = crearNegocioConPrimerPago(LocalDate.now(reloj).plusDays(20));
        Cookie admin = sesionAdmin();
        mvc.perform(put("/api/admin/negocios/" + negocio + "/plan").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("planId", planLite(), "montoMensual", 59))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/negocios/" + negocio).cookie(admin))
                .andExpect(jsonPath("$.suscripcion.montoMensual").value(59.0))
                .andExpect(jsonPath("$.planEfectivo.modeloVideo").value("Gemini Omni Flash"));
    }

    @Test
    void elAdministradorEntraComoElNegocioYVuelveAlPanel() throws Exception {
        String correo = correoUnico();
        Cookie negocio = sesionNegocioListo(correo, "MiClave2026");
        Long negocioId = json.readTree(mvc.perform(get("/api/auth/yo").cookie(negocio))
                .andReturn().getResponse().getContentAsString()).get("negocioId").asLong();

        var entrada = mvc.perform(post("/api/admin/negocios/" + negocioId + "/entrar").with(csrf())
                        .cookie(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("NEGOCIO"))
                .andExpect(jsonPath("$.administrador").value(CORREO_ADMIN))
                .andReturn();
        Cookie comoNegocio = entrada.getResponse().getCookie(CookieDeSesion.NOMBRE);

        mvc.perform(get("/api/auth/yo").cookie(comoNegocio))
                .andExpect(jsonPath("$.negocioId").value(negocioId))
                .andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.administrador").value(CORREO_ADMIN));
        mvc.perform(get("/api/perfil").cookie(comoNegocio)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/negocios").cookie(comoNegocio)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/cambiar-contrasena").with(csrf()).cookie(comoNegocio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("contrasenaActual", CONTRASENA_ADMIN,
                                "nueva", "OtraClave2026", "confirmacion", "OtraClave2026"))))
                .andExpect(status().isForbidden());
        // La contraseña del administrador no cambió.
        sesionAdmin();

        Cookie deVuelta = mvc.perform(post("/api/auth/volver-al-panel").with(csrf()).cookie(comoNegocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        mvc.perform(get("/api/admin/negocios").cookie(deVuelta)).andExpect(status().isOk());

        // El negocio no puede entrar como otro negocio ni usar "volver al panel".
        mvc.perform(post("/api/admin/negocios/" + negocioId + "/entrar").with(csrf()).cookie(negocio))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/volver-al-panel").with(csrf()).cookie(negocio))
                .andExpect(status().isForbidden());
    }

    @Test
    void losCostosDelMesSeConsultanPorMes() throws Exception {
        Cookie admin = sesionAdmin();
        mvc.perform(get("/api/admin/costos").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value(java.time.YearMonth.now(reloj).toString()))
                .andExpect(jsonPath("$.negocios").isArray());
        mvc.perform(get("/api/admin/costos").param("mes", "noviembre").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MES_INVALIDO"));
    }

    private Long crearNegocioConPrimerPago(LocalDate fecha) throws Exception {
        Map<String, Object> solicitud = new HashMap<>(solicitudNegocio(correoUnico()));
        solicitud.put("fechaPrimerPago", fecha.toString());
        return json.readTree(crearNegocio(sesionAdmin(), solicitud).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("negocioId").asLong();
    }

    private List<TipoNotificacion> tipos(Long negocioId) {
        return repositorioNotificaciones.findAll().stream().filter(n -> negocioId.equals(n.getNegocioId()))
                .map(NotificacionAdmin::getTipo).toList();
    }

    private ResultActions registrarPago(Cookie admin, Long negocio, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/admin/negocios/" + negocio + "/pagos").with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions otorgar(Cookie admin, String ruta, Long plan, Instant desde, Instant hasta, String motivo)
            throws Exception {
        return mvc.perform(post(ruta).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("planId", plan, "desde", desde.toString(),
                        "hasta", hasta.toString(), "motivo", motivo))));
    }

    private void guardarCredencial(Cookie admin, String nombre, String valor) throws Exception {
        mvc.perform(put("/api/admin/credenciales").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombre", nombre, "valor", valor))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> plan(String nombre, Long modeloVideo, boolean activo) {
        Long gemini = modelos.findAll().stream().filter(m -> m.getAdaptador().equals("GEMINI")).findFirst()
                .orElseThrow().getId();
        Map<String, Object> plan = new HashMap<>();
        plan.put("nombre", nombre);
        plan.put("descripcion", "Plan de prueba");
        plan.put("videosPorDia", 2);
        plan.put("regeneracionesPorVideo", 5);
        plan.put("modeloTextoId", gemini);
        plan.put("modeloVideoId", modeloVideo);
        plan.put("precioReferencial", new BigDecimal("99.00"));
        plan.put("activo", activo);
        return plan;
    }

    private Long modelo(String adaptador) {
        return modelos.findAll().stream().filter(m -> m.getAdaptador().equals(adaptador)).findFirst().orElseThrow()
                .getId();
    }
}
