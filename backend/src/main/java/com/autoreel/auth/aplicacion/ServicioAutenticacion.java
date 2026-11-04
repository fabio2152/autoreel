package com.autoreel.auth.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.autoreel.auth.dominio.ReglasDeContrasena;
import com.autoreel.auth.dominio.Usuario;
import com.autoreel.auth.infraestructura.UsuarioRepositorio;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioAutenticacion {

    /** RN-08: después de 5 intentos fallidos, la cuenta se bloquea durante 15 minutos. */
    public static final int MAXIMO_INTENTOS = 5;
    public static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final Clock reloj;
    /** Se compara contra él cuando el correo no existe, para que la respuesta tarde lo mismo y no lo delate. */
    private final String hashFicticio;

    public ServicioAutenticacion(UsuarioRepositorio usuarios, PasswordEncoder codificador, Clock reloj) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.reloj = reloj;
        this.hashFicticio = codificador.encode("contrasena-ficticia-para-comparar");
    }

    /**
     * Valida las credenciales. Las excepciones no deshacen la transacción, para que el intento fallido quede
     * registrado.
     */
    @Transactional(noRollbackFor = ExcepcionDeNegocio.class)
    public Usuario iniciarSesion(String correo, String contrasena) {
        Instant ahora = reloj.instant();
        Usuario usuario = usuarios.buscarPorCorreo(Usuario.normalizarCorreo(correo)).orElse(null);
        if (usuario == null) {
            codificador.matches(contrasena, hashFicticio);
            throw credencialesInvalidas();
        }
        if (usuario.estaBloqueado(ahora)) {
            throw new ExcepcionDeNegocio(HttpStatus.LOCKED, "CUENTA_BLOQUEADA",
                    "Tu cuenta está bloqueada por varios intentos fallidos. Vuelve a intentarlo en 15 minutos.");
        }
        if (!codificador.matches(contrasena, usuario.getContrasenaHash())) {
            usuarios.registrarIntentoFallido(usuario.getId(), MAXIMO_INTENTOS, ahora.plus(DURACION_BLOQUEO));
            throw credencialesInvalidas();
        }
        if (!usuario.isActivo()) {
            throw credencialesInvalidas();
        }
        if (usuario.negocioSuspendido()) {
            throw new ExcepcionDeNegocio(HttpStatus.FORBIDDEN, "NEGOCIO_SUSPENDIDO",
                    "Tu cuenta está suspendida. Comunícate con el administrador de AutoReel.");
        }
        if (usuario.contrasenaTemporalVencida(ahora)) {
            throw new ExcepcionDeNegocio(HttpStatus.UNAUTHORIZED, "CONTRASENA_TEMPORAL_VENCIDA",
                    "Tu contraseña temporal venció. Pide una nueva al administrador de AutoReel.");
        }
        usuario.registrarAccesoExitoso(ahora);
        return usuario;
    }

    /**
     * RN-04 y RN-05: valida la contraseña nueva, la guarda e invalida las sesiones anteriores.
     * Devuelve el usuario actualizado para emitir la nueva sesión.
     */
    @Transactional
    public Usuario cambiarContrasena(Long idUsuario, String contrasenaActual, String nueva, String confirmacion) {
        Usuario usuario = usuarios.buscarConNegocio(idUsuario)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos tu cuenta."));
        // Quien entra con contraseña temporal acaba de escribirla al iniciar sesión (RN-03); los demás confirman
        // su contraseña actual.
        if (!usuario.isDebeCambiarContrasena()
                && (contrasenaActual == null || !codificador.matches(contrasenaActual, usuario.getContrasenaHash()))) {
            throw new ExcepcionDeNegocio("CONTRASENA_ACTUAL_INCORRECTA", "La contraseña actual no es correcta.");
        }
        if (usuario.contrasenaTemporalVencida(reloj.instant())) {
            throw new ExcepcionDeNegocio(HttpStatus.UNAUTHORIZED, "CONTRASENA_TEMPORAL_VENCIDA",
                    "Tu contraseña temporal venció. Pide una nueva al administrador de AutoReel.");
        }
        ReglasDeContrasena.validar(nueva, confirmacion);
        if (codificador.matches(nueva, usuario.getContrasenaHash())) {
            throw new ExcepcionDeNegocio("CONTRASENA_REPETIDA", "La contraseña nueva debe ser distinta de la actual.");
        }
        usuario.cambiarContrasena(codificador.encode(nueva));
        return usuario;
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long idUsuario) {
        return usuarios.buscarConNegocio(idUsuario)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos tu cuenta."));
    }

    private static ExcepcionDeNegocio credencialesInvalidas() {
        return new ExcepcionDeNegocio(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                "El correo o la contraseña no son correctos.");
    }
}
