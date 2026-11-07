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
import com.autoreel.costos.aplicacion.ServicioCostos;
import com.autoreel.facturacion.aplicacion.ResolutorDePlanEfectivo;
import com.autoreel.facturacion.dominio.BeneficioTemporal;
import com.autoreel.facturacion.dominio.EstadoPago;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.facturacion.dominio.Suscripcion;
import com.autoreel.facturacion.infraestructura.BeneficioRepositorio;
import com.autoreel.facturacion.infraestructura.PlanRepositorio;
import com.autoreel.facturacion.infraestructura.SuscripcionRepositorio;
import com.autoreel.negocio.dominio.Negocio;
import com.autoreel.negocio.infraestructura.NegocioRepositorio;
import com.autoreel.negocio.aplicacion.ServicioPerfil;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.video.aplicacion.ServicioConsumo;
import com.autoreel.video.dominio.Plano;
import com.autoreel.video.infraestructura.VideoRepositorio;
import org.springframework.data.domain.Limit;
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
    private final ServicioPerfil perfiles;
    private final ServicioConsumo consumo;
    private final VideoRepositorio videos;
    private final ResolutorDePlanEfectivo planesEfectivos;
    private final BeneficioRepositorio beneficios;
    private final ServicioCostos costos;
    private final Clock reloj;

    public ServicioGestionNegocios(NegocioRepositorio negocios, UsuarioRepositorio usuarios,
            SuscripcionRepositorio suscripciones, PlanRepositorio planes, RegistroDeNichos nichos,
            GeneradorDeContrasenaTemporal generador, PasswordEncoder codificador, PropiedadesAplicacion propiedades,
            ServicioPerfil perfiles, ServicioConsumo consumo, VideoRepositorio videos,
            ResolutorDePlanEfectivo planesEfectivos, BeneficioRepositorio beneficios, ServicioCostos costos,
            Clock reloj) {
        this.negocios = negocios;
        this.usuarios = usuarios;
        this.suscripciones = suscripciones;
        this.planes = planes;
        this.nichos = nichos;
        this.generador = generador;
        this.codificador = codificador;
        this.propiedades = propiedades;
        this.perfiles = perfiles;
        this.consumo = consumo;
        this.videos = videos;
        this.planesEfectivos = planesEfectivos;
        this.beneficios = beneficios;
        this.costos = costos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<NegocioResumen> listar(String busqueda, EstadoPago estadoPago) {
        LocalDate hoy = LocalDate.now(reloj);
        Map<Long, Usuario> usuariosPorNegocio = usuarios.listarUsuariosDeNegocio().stream()
                .collect(Collectors.toMap(usuario -> usuario.getNegocio().getId(), Function.identity()));
        Map<Long, Plan> conBeneficio = beneficios.vigentes(reloj.instant()).stream()
                .collect(Collectors.toMap(BeneficioTemporal::getNegocioId, BeneficioTemporal::getPlan,
                        (primero, segundo) -> primero));
        String texto = busqueda == null ? "" : busqueda.trim().toLowerCase(Locale.ROOT);
        return suscripciones.listarConNegocioYPlan().stream()
                .map(suscripcion -> resumen(suscripcion, usuariosPorNegocio.get(suscripcion.getNegocio().getId()),
                        conBeneficio.get(suscripcion.getNegocio().getId()), hoy))
                .filter(fila -> texto.isEmpty() || fila.nombre().toLowerCase(Locale.ROOT).contains(texto)
                        || (fila.correo() != null && fila.correo().contains(texto)))
                .filter(fila -> estadoPago == null || fila.estadoPago() == estadoPago)
                .sorted(Comparator.comparing(NegocioResumen::proximaFechaPago).thenComparing(NegocioResumen::nombre))
                .toList();
    }

    @Transactional
    public ContrasenaTemporal crear(SolicitudCrearNegocio solicitud) {
        PaqueteNicho nicho = nichos.buscar(solicitud.nicho())
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
        perfiles.crearVacio(negocio);
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
        String nombreNicho = nichos.buscar(negocio.getNicho()).map(PaqueteNicho::nombre).orElse(negocio.getNicho());
        ResolutorDePlanEfectivo.PlanEfectivo efectivo = planesEfectivos.resolver(negocioId);
        Plan plan = efectivo.plan();
        return new NegocioDetalle(negocio.getId(), negocio.getNombre(), negocio.getNicho(), nombreNicho,
                negocio.getEstado(), negocio.isOnboardingCompleto(), negocio.getFechaCreacion(),
                new NegocioDetalle.Usuario(usuario.getCorreo(), usuario.getUltimoAcceso(),
                        usuario.isDebeCambiarContrasena(), usuario.getContrasenaTemporalVence(),
                        usuario.estaBloqueado(ahora)),
                new NegocioDetalle.Suscripcion(suscripcion.getPlan().getId(), suscripcion.getPlan().getNombre(),
                        suscripcion.getMontoMensual(), suscripcion.getFechaInicio(),
                        suscripcion.getProximaFechaPago(), suscripcion.getDiaPago(), suscripcion.getDiasAviso(),
                        suscripcion.estadoPago(LocalDate.now(reloj))),
                new NegocioDetalle.PlanEfectivo(plan.getId(), plan.getNombre(), plan.getModeloTexto().getNombre(),
                        plan.getModeloVideo().getNombre(), plan.getVideosPorDia(), plan.getRegeneracionesPorVideo(),
                        efectivo.beneficio() == null ? null : efectivo.beneficio().getHasta()),
                costos.deNegocio(negocioId, costos.mesActual()),
                perfiles.paraAdmin(negocioId),
                new NegocioDetalle.Videos(consumo.delMes(negocioId),
                        videos.listarDeNegocio(negocioId, Limit.of(20)).stream()
                                .map(video -> new NegocioDetalle.VideoDelNegocio(video.getId(), video.getTipoVideo(),
                                        video.getEstado(), video.getFechaCreacion(),
                                        video.getModeloVideoUsado().getNombre(), video.getCostoEstimado(),
                                        video.planosConFoto().stream().filter(Plano::isUsoRespaldo).count(),
                                        video.getMotivoError()))
                                .toList()));
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

    private NegocioResumen resumen(Suscripcion suscripcion, Usuario usuario, Plan beneficio, LocalDate hoy) {
        Negocio negocio = suscripcion.getNegocio();
        Plan plan = beneficio != null ? beneficio : suscripcion.getPlan();
        return new NegocioResumen(negocio.getId(), negocio.getNombre(), usuario == null ? null : usuario.getCorreo(),
                negocio.getNicho(), plan.getNombre(), plan.getModeloVideo().getNombre(), beneficio != null,
                suscripcion.getProximaFechaPago(),
                suscripcion.estadoPago(hoy), (int) consumo.delMes(negocio.getId()), negocio.getEstado(),
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
