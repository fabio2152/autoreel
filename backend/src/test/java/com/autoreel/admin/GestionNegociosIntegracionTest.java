package com.autoreel.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.autoreel.PruebaDeIntegracion;
import com.autoreel.auth.infraestructura.UsuarioRepositorio;
import com.autoreel.ia.infraestructura.ModeloIARepositorio;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class GestionNegociosIntegracionTest extends PruebaDeIntegracion {

    @Autowired
    UsuarioRepositorio usuarios;

    @Autowired
    ModeloIARepositorio modelos;

    @Test
    void v01CrearNegocioMuestraLaContrasenaUnaVezYApareceEnLaLista() throws Exception {
        String correo = correoUnico();
        Cookie admin = sesionAdmin();

        JsonNode creado = json.readTree(crearNegocio(admin, solicitudNegocio(correo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enlace").value("http://localhost:5173/login"))
                .andReturn().getResponse().getContentAsString());
        String temporal = creado.get("contrasenaTemporal").asString();
        long negocioId = creado.get("negocioId").asLong();

        assertThat(temporal).hasSize(12);
        // Solo se guarda el hash (RN-02).
        assertThat(usuarios.buscarPorCorreo(correo).orElseThrow().getContrasenaHash()).isNotEqualTo(temporal)
                .startsWith("$2");

        mvc.perform(get("/api/admin/negocios").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].correo", hasItem(correo)));
        String detalle = mvc.perform(get("/api/admin/negocios/" + negocioId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suscripcion.plan").value("Lite"))
                .andExpect(jsonPath("$.suscripcion.estadoPago").value("AL_DIA"))
                .andExpect(jsonPath("$.suscripcion.fechaInicio").value(LocalDate.now(reloj).toString()))
                .andReturn().getResponse().getContentAsString();
        // La contraseña temporal no vuelve a mostrarse.
        assertThat(detalle).doesNotContain(temporal).doesNotContain("contrasenaHash");
    }

    @Test
    void v02CorreoYaRegistradoMuestraUnError() throws Exception {
        String correo = correoUnico();
        negocioCreado(correo);

        crearNegocio(sesionAdmin(), solicitudNegocio(correo.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_YA_REGISTRADO"));
    }

    @Test
    void v06ReiniciarContrasenaInvalidaLaAnteriorYCierraLaSesion() throws Exception {
        String correo = correoUnico();
        Cookie sesionAbierta = sesionNegocioListo(correo, "MiClave2026");
        long negocioId = usuarios.buscarPorCorreo(correo).orElseThrow().getNegocio().getId();

        String nueva = json.readTree(mvc.perform(post("/api/admin/negocios/" + negocioId + "/reiniciar-contrasena")
                        .with(csrf()).cookie(sesionAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("contrasenaTemporal").asString();

        mvc.perform(get("/api/auth/yo").cookie(sesionAbierta)).andExpect(status().isUnauthorized());
        iniciarSesion(correo, "MiClave2026").andExpect(status().isUnauthorized());
        iniciarSesion(correo, nueva).andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true));
    }

    @Test
    void reiniciarLaContrasenaDesbloqueaLaCuenta() throws Exception {
        String correo = correoUnico();
        sesionNegocioListo(correo, "MiClave2026");
        long negocioId = usuarios.buscarPorCorreo(correo).orElseThrow().getNegocio().getId();
        for (int i = 0; i < 5; i++) {
            iniciarSesion(correo, "Equivocada" + i);
        }
        iniciarSesion(correo, "MiClave2026").andExpect(status().isLocked());

        String nueva = json.readTree(mvc.perform(post("/api/admin/negocios/" + negocioId + "/reiniciar-contrasena")
                        .with(csrf()).cookie(sesionAdmin()))
                .andReturn().getResponse().getContentAsString()).get("contrasenaTemporal").asString();

        iniciarSesion(correo, nueva).andExpect(status().isOk());
    }

    @Test
    void v07ElNegocioNoPuedeUsarLaApiDelAdministrador() throws Exception {
        Cookie negocio = sesionNegocioListo(correoUnico(), "MiClave2026");

        mvc.perform(get("/api/admin/negocios").cookie(negocio))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        crearNegocio(negocio, solicitudNegocio(correoUnico())).andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorNoPuedeUsarLaApiDelNegocio() throws Exception {
        mvc.perform(get("/api/perfil").cookie(sesionAdmin())).andExpect(status().isForbidden());
    }

    @Test
    void v09NegocioSuspendidoNoPuedeIniciarSesionYSuSesionSeCierra() throws Exception {
        String correo = correoUnico();
        Cookie sesionAbierta = sesionNegocioListo(correo, "MiClave2026");
        long negocioId = usuarios.buscarPorCorreo(correo).orElseThrow().getNegocio().getId();
        Cookie admin = sesionAdmin();

        mvc.perform(post("/api/admin/negocios/" + negocioId + "/suspender").with(csrf()).cookie(admin))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/yo").cookie(sesionAbierta)).andExpect(status().isUnauthorized());
        iniciarSesion(correo, "MiClave2026")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("NEGOCIO_SUSPENDIDO"));

        mvc.perform(post("/api/admin/negocios/" + negocioId + "/reactivar").with(csrf()).cookie(admin))
                .andExpect(status().isNoContent());
        iniciarSesion(correo, "MiClave2026").andExpect(status().isOk());
    }

    @Test
    void v33CrearNegocioSinPlanSinMontoOSinFechaMuestraUnError() throws Exception {
        Cookie admin = sesionAdmin();
        for (String campo : new String[] {"planId", "montoMensual", "fechaPrimerPago"}) {
            Map<String, Object> solicitud = new HashMap<>(solicitudNegocio(correoUnico()));
            solicitud.remove(campo);
            crearNegocio(admin, solicitud)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }
        Map<String, Object> montoCero = new HashMap<>(solicitudNegocio(correoUnico()));
        montoCero.put("montoMensual", 0);
        crearNegocio(admin, montoCero).andExpect(status().isBadRequest());
    }

    @Test
    void nichoDesconocidoSeRechaza() throws Exception {
        Map<String, Object> solicitud = new HashMap<>(solicitudNegocio(correoUnico()));
        solicitud.put("nicho", "FLORERIA");
        crearNegocio(sesionAdmin(), solicitud)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NICHO_INVALIDO"));
    }

    @Test
    void filtroPorEstadoDePagoYBusqueda() throws Exception {
        String correo = correoUnico();
        Map<String, Object> solicitud = new HashMap<>(solicitudNegocio(correo));
        solicitud.put("nombre", "Postres Vencidos SAC");
        solicitud.put("fechaPrimerPago", LocalDate.now(reloj).minusDays(1).toString());
        crearNegocio(sesionAdmin(), solicitud).andExpect(status().isCreated());

        mvc.perform(get("/api/admin/negocios").param("estadoPago", "VENCIDO").param("busqueda", "vencidos")
                        .cookie(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].correo", hasItem(correo)))
                .andExpect(jsonPath("$[0].estadoPago").value("VENCIDO"));
    }

    @Test
    void lasSemillasDePlanesYModelosSeLeenConSuJson() throws Exception {
        mvc.perform(get("/api/admin/planes").cookie(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Lite"))
                .andExpect(jsonPath("$[0].videosPorDia").value(1))
                .andExpect(jsonPath("$[0].regeneracionesPorVideo").value(3))
                .andExpect(jsonPath("$[0].modeloTexto").value("Gemini"))
                .andExpect(jsonPath("$[0].modeloVideo").value("Veo 3.1 Lite"));

        var veo = modelos.findAll().stream().filter(m -> m.getAdaptador().equals("VEO")).findFirst().orElseThrow();
        assertThat(veo.getParametros()).containsEntry("relacionAspecto", "9:16");
        assertThat(planes.findAll().getFirst().getOpcionesProduccionPermitidas()).containsEntry("vozEnOff", false);
    }
}
