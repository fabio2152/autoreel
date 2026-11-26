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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.autoreel.Imagenes;
import com.autoreel.PruebaDeIntegracion;
import com.autoreel.compartido.seguridad.CookieDeSesion;
import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.costos.dominio.RegistroCosto;
import com.autoreel.costos.infraestructura.RegistroCostoRepositorio;
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

    @Autowired
    ServicioNotificaciones notificaciones;

    @Autowired
    NotificacionRepositorio repositorioNotificaciones;

    @Autowired
    ResolutorDePlanEfectivo planesEfectivos;

    @Autowired
    ModeloIARepositorio modelos;

    @Autowired
    RegistroCostoRepositorio registrosDeCosto;

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
    void unPagoSeRegistraConSuComprobanteOSinEl() throws Exception {
        LocalDate fecha = LocalDate.now(reloj).plusDays(3);
        Long negocio = crearNegocioConPrimerPago(fecha);
        Long otroNegocio = crearNegocioConPrimerPago(fecha);
        Cookie admin = sesionAdmin();
        String hoy = LocalDate.now(reloj).toString();

        pedirSubidaComprobante(admin, negocio, "application/pdf", 1000)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_PERMITIDO"))
                .andExpect(jsonPath("$.mensaje").value("El comprobante debe ser una imagen JPG, PNG o WebP."));
        pedirSubidaComprobante(admin, negocio, "image/jpeg", 5L * 1024 * 1024 + 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El archivo pesa más de 5 MB."));
        pedirSubidaComprobante(sesionNegocioListo(correoUnico(), "MiClave2026"), negocio, "image/jpeg", 1000)
                .andExpect(status().isForbidden());

        // Un comprobante subido a la carpeta de otro negocio no sirve para este.
        String ajeno = subirComprobante(admin, otroNegocio);
        registrarPago(admin, negocio, Map.of("fechaPago", hoy, "monto", 49, "medio", "YAPE",
                "comprobanteClave", ajeno))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_INVALIDO"));

        String clave = subirComprobante(admin, negocio);
        assertThat(clave).startsWith("subidas/negocios/" + negocio + "/comprobantes/");
        String definitiva = clave.substring("subidas/".length());
        registrarPago(admin, negocio, Map.of("fechaPago", hoy, "monto", 49, "medio", "YAPE",
                "comprobanteClave", clave))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.periodoCubierto").value(fecha.toString()))
                .andExpect(jsonPath("$.pago.comprobanteUrl").value("memoria://leer/" + definitiva));
        assertThat(almacen.existe(definitiva)).isTrue();
        assertThat(almacen.existe(clave)).isFalse();

        registrarPago(admin, negocio, Map.of("fechaPago", hoy, "monto", 49, "medio", "EFECTIVO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.periodoCubierto").value(fecha.plusMonths(1).toString()))
                .andExpect(jsonPath("$.pago.comprobanteUrl").isEmpty());

        JsonNode pagos = json.readTree(mvc.perform(get("/api/admin/negocios/" + negocio + "/pagos").cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(pagos.valueStream().map(pago -> pago.get("comprobanteUrl"))
                .map(url -> url.isNull() ? null : url.asString()).toList())
                .containsExactlyInAnyOrder("memoria://leer/" + definitiva, null);
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
        assertThat(nombres).contains("Gemini", "Gemini Omni Flash", "Veo 3.1 Lite", "Veo 3.1 Fast", "Veo 3.1");

        mvc.perform(post("/api/admin/modelos-ia/" + modelo("GEMINI_OMNI") + "/probar").with(csrf()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitosa").value(false))
                .andExpect(jsonPath("$.mensaje").value("No hay credencial configurada para autoreel/gemini."));
    }

    @Test
    void v38NoSePuedeDesactivarUnModeloQueUsaUnPlanActivo() throws Exception {
        // Desde V11 el plan Lite hace sus clips con Veo 3.1 Lite.
        mvc.perform(post("/api/admin/modelos-ia/" + veo("veo-3.1-lite-generate-001") + "/desactivar").with(csrf())
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

    /**
     * V40 y V41: un plan Pro de prueba con otro modelo de video (Veo 3.1 Fast), dado como beneficio de 2 usos. Que
     * los videos aprobados gasten los usos (y que un video que falla devuelva el suyo) se prueba en
     * GeneracionIntegracionTest, que tiene los proveedores de IA simulados.
     */
    @Test
    void v40ElBeneficioCambiaElModeloDeVideoYAlTerminarVuelveAlPlanLite() throws Exception {
        Cookie admin = sesionAdmin();
        Long veo = veo("veo-3.1-fast-generate-001");
        String nombrePlan = "Pro " + System.nanoTime() % 100000;
        Long planPro = null;
        try {
            planPro = json.readTree(mvc.perform(post("/api/admin/planes").with(csrf()).cookie(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(plan(nombrePlan, veo, true))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.modeloVideo").value("Veo 3.1 Fast"))
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();

            Long negocio = crearNegocioConPrimerPago(LocalDate.now(reloj).plusDays(20));
            String ruta = "/api/admin/negocios/" + negocio + "/beneficios";
            otorgar(admin, ruta, planPro, 0, "Cortesía")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("La cantidad de usos debe estar entre 1 y 30."));
            otorgar(admin, ruta, planPro, 31, "Cortesía")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("La cantidad de usos debe estar entre 1 y 30."));
            otorgar(admin, ruta, planPro, 2, " ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("Escribe el motivo del beneficio."));
            otorgar(admin, ruta, planLite(), 2, "Cortesía")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("BENEFICIO_MISMO_PLAN"));
            Long beneficio = json.readTree(otorgar(admin, ruta, planPro, 2, "Por el inconveniente de ayer")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("VIGENTE"))
                    .andExpect(jsonPath("$.usosOtorgados").value(2))
                    .andExpect(jsonPath("$.usosConsumidos").value(0))
                    .andExpect(jsonPath("$.desde").doesNotExist())
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();
            otorgar(admin, ruta, planPro, 5, "Otro")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("BENEFICIO_VIGENTE"));

            // El beneficio no vence con el tiempo: solo con sus usos.
            reloj.avanzar(Duration.ofDays(40));
            assertThat(planesEfectivos.de(negocio).getModeloVideo().getNombre()).isEqualTo("Veo 3.1 Fast");
            mvc.perform(get("/api/admin/negocios/" + negocio).cookie(admin))
                    .andExpect(jsonPath("$.planEfectivo.plan").value(nombrePlan))
                    .andExpect(jsonPath("$.planEfectivo.modeloVideo").value("Veo 3.1 Fast"))
                    .andExpect(jsonPath("$.planEfectivo.beneficioUsosOtorgados").value(2))
                    .andExpect(jsonPath("$.planEfectivo.beneficioUsosRestantes").value(2))
                    .andExpect(jsonPath("$.suscripcion.plan").value("Lite"));
            mvc.perform(get("/api/admin/negocios").cookie(admin))
                    .andExpect(jsonPath("$[?(@.id == " + negocio + ")].conBeneficio").value(true))
                    .andExpect(jsonPath("$[?(@.id == " + negocio + ")].beneficioUsosRestantes").value(2));

            mvc.perform(delete(ruta + "/" + beneficio).with(csrf()).cookie(admin))
                    .andExpect(status().isNoContent());
            assertThat(planesEfectivos.de(negocio).getModeloVideo().getNombre()).isEqualTo("Veo 3.1 Lite");
            mvc.perform(get(ruta).cookie(admin))
                    .andExpect(jsonPath("$[0].estado").value("TERMINADO"))
                    .andExpect(jsonPath("$[0].usosConsumidos").value(0));
            mvc.perform(get("/api/admin/negocios/" + negocio).cookie(admin))
                    .andExpect(jsonPath("$.planEfectivo.plan").value("Lite"))
                    .andExpect(jsonPath("$.planEfectivo.beneficioUsosRestantes").doesNotExist());
            // Terminado el anterior, se puede otorgar otro.
            otorgar(admin, ruta, planPro, 1, "Otra cortesía").andExpect(status().isCreated());
        } finally {
            reloj.reiniciar();
            if (planPro != null) {
                mvc.perform(put("/api/admin/planes/" + planPro).with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(plan(nombrePlan, veo, false))));
            }
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
                .andExpect(jsonPath("$.planEfectivo.modeloVideo").value("Veo 3.1 Lite"));
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
    void elNegocioQueOlvidoSuContrasenaLeAvisaAlAdministrador() throws Exception {
        String correo = correoUnico();
        Cookie negocio = sesionNegocioListo(correo, "MiClave2026");
        Long negocioId = json.readTree(mvc.perform(get("/api/auth/yo").cookie(negocio))
                .andReturn().getResponse().getContentAsString()).get("negocioId").asLong();

        solicitarReinicio(negocio, Map.of("mensaje", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Escribe un mensaje para el administrador."));
        solicitarReinicio(negocio, Map.of("contacto", "llámame", "mensaje", "Hola"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Escribe el WhatsApp solo con números."));
        assertThat(tipos(negocioId)).isEmpty();

        solicitarReinicio(negocio, Map.of("contacto", "+51 987 654 321", "mensaje", "Olvidé mi\ncontraseña, gracias"))
                .andExpect(status().isNoContent());
        // Una por día: la segunda del mismo día no crea otra.
        solicitarReinicio(negocio, Map.of("mensaje", "¿Me ayudas?")).andExpect(status().isNoContent());
        List<NotificacionAdmin> deNegocio = repositorioNotificaciones.findAll().stream()
                .filter(n -> negocioId.equals(n.getNegocioId())).toList();
        assertThat(deNegocio).singleElement().satisfies(n -> {
            assertThat(n.getTipo()).isEqualTo(TipoNotificacion.SOLICITUD_CONTRASENA);
            assertThat(n.getMensaje()).isEqualTo("Pastelería Dulce Encanto olvidó su contraseña y pide una temporal. "
                    + "Correo: " + correo + ". WhatsApp: +51 987 654 321. Mensaje: Olvidé mi contraseña, gracias");
            assertThat(n.isLeida()).isFalse();
        });
        Cookie admin = sesionAdmin();
        mvc.perform(get("/api/admin/notificaciones").cookie(admin))
                .andExpect(jsonPath("$.notificaciones[?(@.negocioId == " + negocioId + ")].tipo")
                        .value("SOLICITUD_CONTRASENA"));

        // Registrar un pago atiende los avisos de pago, no la solicitud de contraseña.
        registrarPago(admin, negocioId, Map.of("fechaPago", LocalDate.now(reloj).toString(), "monto", 49,
                "medio", "YAPE")).andExpect(status().isCreated());
        assertThat(repositorioNotificaciones.findById(deNegocio.getFirst().getId()).orElseThrow().isLeida())
                .isFalse();

        // Al día siguiente puede volver a pedir.
        reloj.avanzar(Duration.ofDays(1));
        solicitarReinicio(negocio, Map.of("mensaje", "Sigo sin poder entrar")).andExpect(status().isNoContent());
        assertThat(tipos(negocioId)).containsExactly(TipoNotificacion.SOLICITUD_CONTRASENA,
                TipoNotificacion.SOLICITUD_CONTRASENA);

        // El administrador que entró como el negocio no puede pedirla, ni el administrador desde su panel.
        Cookie comoNegocio = mvc.perform(post("/api/admin/negocios/" + negocioId + "/entrar").with(csrf())
                        .cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        solicitarReinicio(comoNegocio, Map.of("mensaje", "Hola"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCION_NO_PERMITIDA_COMO_ADMINISTRADOR"));
        solicitarReinicio(sesionAdmin(), Map.of("mensaje", "Hola")).andExpect(status().isForbidden());
        assertThat(tipos(negocioId)).hasSize(2);
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

    @Test
    void losCostosDiariosSeAcumulanPorDiaDeLimaYCuadranConLaTabla() throws Exception {
        Cookie admin = sesionAdmin();
        Long negocio = crearNegocioConPrimerPago(LocalDate.now(reloj).plusDays(30));
        registrarCosto(negocio, ConceptoCosto.GUION, "0.05", LocalDateTime.of(2024, 2, 3, 10, 0));
        // 23:30 en Lima ya es el 4 en UTC: cuenta para el 3.
        registrarCosto(negocio, ConceptoCosto.CLIP, "0.10", LocalDateTime.of(2024, 2, 3, 23, 30));
        registrarCosto(negocio, ConceptoCosto.PRUEBA, "0.25", LocalDateTime.of(2024, 2, 10, 8, 0));

        JsonNode diario = json.readTree(mvc.perform(get("/api/admin/costos/diario").param("mes", "2024-02")
                .cookie(admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(diario.get("mes").asString()).isEqualTo("2024-02");
        assertThat(diario.get("dias")).hasSize(29);
        JsonNode serie = serieDe(diario, negocio);
        assertThat(serie).hasSize(29);
        assertThat(serie.get(1).decimalValue()).isEqualByComparingTo("0");
        assertThat(serie.get(2).decimalValue()).isEqualByComparingTo("0.15");
        assertThat(serie.get(8).decimalValue()).isEqualByComparingTo("0.15");
        assertThat(serie.get(9).decimalValue()).isEqualByComparingTo("0.40");
        assertThat(serie.get(28).decimalValue()).isEqualByComparingTo("0.40");

        JsonNode tabla = json.readTree(mvc.perform(get("/api/admin/costos").param("mes", "2024-02").cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode fila = null;
        for (JsonNode candidata : tabla.get("negocios")) {
            if (candidata.get("negocioId").asLong() == negocio) {
                fila = candidata;
            }
        }
        assertThat(fila).isNotNull();
        assertThat(fila.get("costo").get("total").decimalValue()).isEqualByComparingTo(serie.get(28).decimalValue());

        // El mes en curso llega hasta hoy.
        mvc.perform(get("/api/admin/costos/diario").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value(YearMonth.now(reloj).toString()))
                .andExpect(jsonPath("$.dias.length()").value(LocalDate.now(reloj).getDayOfMonth()));
        mvc.perform(get("/api/admin/costos/diario").param("mes", "2024-13").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MES_INVALIDO"));
    }

    private void registrarCosto(Long negocio, ConceptoCosto concepto, String costo, LocalDateTime enLima) {
        registrosDeCosto.save(new RegistroCosto(negocio, null, "GEMINI", concepto, BigDecimal.ONE,
                new BigDecimal(costo), enLima.atZone(reloj.getZone()).toInstant()));
    }

    private static JsonNode serieDe(JsonNode diario, Long negocio) {
        for (JsonNode serie : diario.get("negocios")) {
            if (serie.get("negocioId").asLong() == negocio) {
                return serie.get("serie");
            }
        }
        throw new AssertionError("El negocio " + negocio + " no aparece en el gráfico");
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

    private ResultActions solicitarReinicio(Cookie cookie, Map<String, ?> cuerpo) throws Exception {
        return mvc.perform(post("/api/auth/solicitar-reinicio").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions registrarPago(Cookie admin, Long negocio, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/admin/negocios/" + negocio + "/pagos").with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions pedirSubidaComprobante(Cookie cookie, Long negocio, String tipo, long tamano)
            throws Exception {
        return mvc.perform(post("/api/admin/negocios/" + negocio + "/pagos/url-subida-comprobante").with(csrf())
                .cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("tipoContenido", tipo, "tamano", tamano))));
    }

    /** Pide la URL firmada como el panel y "sube" una captura de Yape. Devuelve la clave temporal. */
    private String subirComprobante(Cookie admin, Long negocio) throws Exception {
        byte[] captura = Imagenes.jpg(1080, 2340);
        String clave = json.readTree(pedirSubidaComprobante(admin, negocio, "image/jpeg", captura.length)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("clave").asString();
        almacen.guardar(clave, captura, "image/jpeg");
        return clave;
    }

    private ResultActions otorgar(Cookie admin, String ruta, Long plan, int usos, String motivo) throws Exception {
        return mvc.perform(post(ruta).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("planId", plan, "usos", usos, "motivo", motivo))));
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

    private Long veo(String identificador) {
        return modelos.findAll().stream().filter(m -> m.getIdentificadorModelo().equals(identificador)).findFirst()
                .orElseThrow().getId();
    }

    private Long modelo(String adaptador) {
        return modelos.findAll().stream().filter(m -> m.getAdaptador().equals(adaptador)).findFirst().orElseThrow()
                .getId();
    }
}
