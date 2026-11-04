package com.autoreel.auth.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import com.autoreel.negocio.dominio.Negocio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code @DynamicUpdate}: cada operación escribe solo las columnas que cambió, para que un inicio de sesión no
 * pise un reinicio de contraseña o una suspensión hechos al mismo tiempo.
 */
@Entity
@Table(name = "usuario")
@DynamicUpdate
public class Usuario {

    /** RN-02: la contraseña temporal vence a las 48 horas. */
    public static final Duration VIGENCIA_CONTRASENA_TEMPORAL = Duration.ofHours(48);
    /** RN-08: después de 5 intentos fallidos, la cuenta se bloquea durante 15 minutos. */
    public static final int MAXIMO_INTENTOS = 5;
    public static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String correo;

    @Column(nullable = false)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Rol rol;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", updatable = false)
    private Negocio negocio;

    @Column(nullable = false)
    private boolean debeCambiarContrasena;

    private Instant contrasenaTemporalVence;

    @Column(nullable = false)
    private boolean activo;

    private Instant ultimoAcceso;

    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    /** Se incrementa para invalidar todas las sesiones abiertas del usuario (RN-05). */
    @Column(nullable = false)
    private int versionSesion;

    @Column(nullable = false)
    private int intentosFallidos;

    private Instant bloqueadoHasta;

    protected Usuario() {
    }

    private Usuario(String correo, Rol rol, Negocio negocio, Instant ahora) {
        this.correo = normalizarCorreo(correo);
        this.rol = rol;
        this.negocio = negocio;
        this.activo = true;
        this.fechaCreacion = ahora;
    }

    public static Usuario administrador(String correo, String contrasenaHash, Instant ahora) {
        Usuario usuario = new Usuario(correo, Rol.ADMIN, null, ahora);
        usuario.contrasenaHash = contrasenaHash;
        return usuario;
    }

    public static Usuario deNegocio(String correo, Negocio negocio, String hashTemporal, Instant ahora) {
        Usuario usuario = new Usuario(correo, Rol.NEGOCIO, negocio, ahora);
        usuario.asignarContrasenaTemporal(hashTemporal, ahora);
        return usuario;
    }

    public static String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * RN-02 y RN-05: la contraseña anterior deja de funcionar, se cierran las sesiones y se levanta un bloqueo.
     */
    public void asignarContrasenaTemporal(String hashTemporal, Instant ahora) {
        this.contrasenaHash = hashTemporal;
        this.debeCambiarContrasena = true;
        this.contrasenaTemporalVence = ahora.plus(VIGENCIA_CONTRASENA_TEMPORAL);
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
        invalidarSesiones();
    }

    public void cambiarContrasena(String nuevoHash) {
        this.contrasenaHash = nuevoHash;
        this.debeCambiarContrasena = false;
        this.contrasenaTemporalVence = null;
        invalidarSesiones();
    }

    public void invalidarSesiones() {
        this.versionSesion++;
    }

    public void registrarAccesoExitoso(Instant ahora) {
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
        this.ultimoAcceso = ahora;
    }

    /** RN-08: al llegar al máximo, bloquea la cuenta y reinicia el contador para el siguiente período. */
    public void registrarIntentoFallido(Instant ahora) {
        this.intentosFallidos++;
        if (intentosFallidos >= MAXIMO_INTENTOS) {
            this.intentosFallidos = 0;
            this.bloqueadoHasta = ahora.plus(DURACION_BLOQUEO);
        }
    }

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    public boolean contrasenaTemporalVencida(Instant ahora) {
        return debeCambiarContrasena && contrasenaTemporalVence != null && !ahora.isBefore(contrasenaTemporalVence);
    }

    public boolean negocioSuspendido() {
        return negocio != null && negocio.estaSuspendido();
    }

    public Long getId() {
        return id;
    }

    public String getCorreo() {
        return correo;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public Rol getRol() {
        return rol;
    }

    public Negocio getNegocio() {
        return negocio;
    }

    public boolean isDebeCambiarContrasena() {
        return debeCambiarContrasena;
    }

    public Instant getContrasenaTemporalVence() {
        return contrasenaTemporalVence;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getUltimoAcceso() {
        return ultimoAcceso;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public int getVersionSesion() {
        return versionSesion;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }
}
