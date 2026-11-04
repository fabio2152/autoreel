package com.autoreel.admin.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.autoreel.admin.api.ContrasenaTemporal;
import com.autoreel.admin.api.NegocioDetalle;
import com.autoreel.admin.api.NegocioResumen;
import com.autoreel.admin.api.SolicitudCrearNegocio;
import com.autoreel.auth.dominio.GeneradorDeContrasenaTemporal;
import com.autoreel.auth.dominio.Usuario;
import com.autoreel.auth.infraestructura.UsuarioRepositorio;
import com.autoreel.compartido.config.PropiedadesAplicacion;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.facturacion.dominio.EstadoPago;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.facturacion.dominio.Suscripcion;
import com.autoreel.facturacion.infraestructura.PlanRepositorio;
import com.autoreel.facturacion.infraestructura.SuscripcionRepositorio;
import com.autoreel.negocio.dominio.Negocio;
import com.autoreel.negocio.infraestructura.NegocioRepositorio;
import com.autoreel.nicho.Nicho;
import com.autoreel.nicho.RegistroDeNichos;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de negocios por el administrador (RF-02 a RF-06): creación con su suscripción, contraseña temporal,
 * reinicio, suspensión y reactivación.
 */
@Service
public class ServicioGestionNegocios {

    private final NegocioRepositorio negocios;
    private final UsuarioRepositorio usuarios;
    private final SuscripcionRepositorio suscripciones;
    private final PlanRepositorio planes;
    private final RegistroDeNichos nichos;
    private final GeneradorDeContrasenaTemporal generador;
    private final PasswordEncoder codificador;
    private final PropiedadesAplicacion propiedades;
    private final Clock reloj;

    public ServicioGestionNegocios(NegocioRepositorio negocios, UsuarioRepositorio usuarios,
            SuscripcionRepositorio suscripciones, PlanRepositorio planes, RegistroDeNichos nichos,
            GeneradorDeContrasenaTemporal generador, PasswordEncoder codificador, PropiedadesAplicacion propiedades,
            Clock reloj) {
        this.negocios = negocios;
        this.usuarios = usuarios;
        this.suscripciones = suscripciones;
        this.planes = planes;
        this.nichos = nichos;
        this.generador = generador;
        this.codificador = codificador;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<NegocioResumen> listar(String busqueda, EstadoPago estadoPago) {
        LocalDate hoy = LocalDate.now(reloj);
        Map<Long, Usuario> usuariosPorNegocio = usuarios.listarUsuariosDeNegocio().stream()
                .collect(Collectors.toMap(usuario -> usuario.getNegocio().getId(), Function.identity()));
        String texto = busqueda == null ? "" : busqueda.trim().toLowerCase(Locale.ROOT);
        return suscripciones.listarConNegocioYPlan().stream()
                .map(suscripcion -> resumen(suscripcion, usuariosPorNegocio.get(suscripcion.getNegocio().getId()), hoy))
                .filter(fila -> texto.isEmpty() || fila.nombre().toLowerCase(Locale.ROOT).contains(texto)
                        || (fila.correo() != null && fila.correo().contains(texto)))
                .filter(fila -> estadoPago == null || fila.estadoPago() == estadoPago)
                .sorted(Comparator.comparing(NegocioResumen::proximaFechaPago).thenComparing(NegocioResumen::nombre))
                .toList();
    }

    @Transactional
    public ContrasenaTemporal crear(SolicitudCrearNegocio solicitud) {
        Nicho nicho = nichos.buscar(solicitud.nicho())
                .orElseThrow(() -> new ExcepcionDeNegocio("NICHO_INVALIDO", "El nicho elegido no existe."));
        Plan plan = planes.findById(solicitud.planId())
                .filter(Plan::isActivo)
                .orElseThrow(() -> new ExcepcionDeNegocio("PLAN_INVALIDO", "El plan elegido no existe o no está activo."));
        String correo = Usuario.normalizarCorreo(solicitud.correo());
        if (usuarios.existsByCorreo(correo)) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "CORREO_YA_REGISTRADO",
                    "Ya existe una cuenta con ese correo.");
        }
        Instant ahora = reloj.instant();
        Negocio negocio = negocios.save(new Negocio(solicitud.nombre().trim(), nicho.id(), ahora));
        suscripciones.save(new Suscripcion(negocio, plan, solicitud.montoMensual(), LocalDate.now(reloj),
                solicitud.fechaPrimerPago()));
        String contrasena = generador.generar();
        Usuario usuario = usuarios.save(Usuario.deNegocio(correo, negocio, codificador.encode(contrasena), ahora));
        return contrasenaTemporal(usuario, contrasena);
    }

    @Transactional(readOnly = true)
    public NegocioDetalle detalle(Long negocioId) {
        Suscripcion suscripcion = suscripcion(negocioId);
        Negocio negocio = suscripcion.getNegocio();
        Usuario usuario = usuarioDe(negocioId);
        Instant ahora = reloj.instant();
        String nombreNicho = nichos.buscar(negocio.getNicho()).map(Nicho::nombre).orElse(negocio.getNicho());
        return new NegocioDetalle(negocio.getId(), negocio.getNombre(), negocio.getNicho(), nombreNicho,
                negocio.getEstado(), negocio.isOnboardingCompleto(), negocio.getFechaCreacion(),
                new NegocioDetalle.Usuario(usuario.getCorreo(), usuario.getUltimoAcceso(),
                        usuario.isDebeCambiarContrasena(), usuario.getContrasenaTemporalVence(),
                        usuario.estaBloqueado(ahora)),
                new NegocioDetalle.Suscripcion(suscripcion.getPlan().getId(), suscripcion.getPlan().getNombre(),
                        suscripcion.getMontoMensual(), suscripcion.getFechaInicio(),
                        suscripcion.getProximaFechaPago(), suscripcion.getDiasAviso(),
                        suscripcion.estadoPago(LocalDate.now(reloj))));
    }

    /** RF-04 y RN-05: la contraseña anterior deja de funcionar y se cierran las sesiones abiertas. */
    @Transactional
    public ContrasenaTemporal reiniciarContrasena(Long negocioId) {
        Usuario usuario = usuarioDe(negocioId);
        String contrasena = generador.generar();
        usuario.asignarContrasenaTemporal(codificador.encode(contrasena), reloj.instant());
        return contrasenaTemporal(usuario, contrasena);
    }

    /** RF-06 y RN-07: el negocio suspendido no puede iniciar sesión y su sesión abierta se cierra. */
    @Transactional
    public void suspender(Long negocioId) {
        Usuario usuario = usuarioDe(negocioId);
        usuario.getNegocio().suspender();
        usuario.invalidarSesiones();
    }

    @Transactional
    public void reactivar(Long negocioId) {
        usuarioDe(negocioId).getNegocio().reactivar();
    }

    private NegocioResumen resumen(Suscripcion suscripcion, Usuario usuario, LocalDate hoy) {
        Negocio negocio = suscripcion.getNegocio();
        return new NegocioResumen(negocio.getId(), negocio.getNombre(), usuario == null ? null : usuario.getCorreo(),
                negocio.getNicho(), suscripcion.getPlan().getNombre(), suscripcion.getProximaFechaPago(),
                suscripcion.estadoPago(hoy), null, negocio.getEstado(),
                usuario == null ? null : usuario.getUltimoAcceso());
    }

    private ContrasenaTemporal contrasenaTemporal(Usuario usuario, String contrasena) {
        return new ContrasenaTemporal(usuario.getNegocio().getId(), usuario.getCorreo(), contrasena,
                propiedades.enlaceInicioSesion(), usuario.getContrasenaTemporalVence());
    }

    private Suscripcion suscripcion(Long negocioId) {
        return suscripciones.buscarPorNegocio(negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos ese negocio."));
    }

    private Usuario usuarioDe(Long negocioId) {
        return usuarios.buscarPorNegocio(negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos ese negocio."));
    }
}
