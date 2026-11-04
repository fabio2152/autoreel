package com.autoreel.auth.aplicacion;

import java.time.Clock;
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

    private final UsuarioRepositorio usuarios;
    private final IntentosDeCorreosDesconocidos correosDesconocidos;
    private final PasswordEncoder codificador;
    private final Clock reloj;
    /**
     * Se compara contra él cuando no se revisa la contraseña real (correo inexistente o cuenta bloqueada),
     * para que todas las respuestas tarden lo mismo y no delaten qué correos existen.
     */
    private final String hashFicticio;

    public ServicioAutenticacion(UsuarioRepositorio usuarios, IntentosDeCorreosDesconocidos correosDesconocidos,
            PasswordEncoder codificador, Clock reloj) {
        this.usuarios = usuarios;
        this.correosDesconocidos = correosDesconocidos;
        this.codificador = codificador;
        this.reloj = reloj;
        this.hashFicticio = codificador.encode("contrasena-ficticia-para-comparar");
    }

    /**
     * Valida las credenciales. La fila del usuario se bloquea durante la verificación, así que los intentos
     * simultáneos se cuentan uno a uno (RN-08). Las excepciones no deshacen la transacción, para que el intento
     * fallido quede registrado.
     */
    @Transactional(noRollbackFor = ExcepcionDeNegocio.class)
    public Usuario iniciarSesion(String correo, String contrasena) {
        Instant ahora = reloj.instant();
        String correoNormalizado = Usuario.normalizarCorreo(correo);
        Usuario usuario = usuarios.bloquearPorCorreo(correoNormalizado).orElse(null);
        if (usuario == null) {
            codificador.matches(contrasena, hashFicticio);
            if (correosDesconocidos.estaBloqueado(correoNormalizado, ahora)) {
                throw cuentaBloqueada();
            }
            correosDesconocidos.registrarFallo(correoNormalizado, ahora);
            throw credencialesInvalidas();
        }
        if (usuario.estaBloqueado(ahora)) {
            codificador.matches(contrasena, hashFicticio);
            throw cuentaBloqueada();
        }
        if (!codificador.matches(contrasena, usuario.getContrasenaHash())) {
            usuario.registrarIntentoFallido(ahora);
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

    private static ExcepcionDeNegocio cuentaBloqueada() {
        return new ExcepcionDeNegocio(HttpStatus.LOCKED, "CUENTA_BLOQUEADA",
                "Tu cuenta está bloqueada por varios intentos fallidos. Vuelve a intentarlo en 15 minutos.");
    }

    private static ExcepcionDeNegocio credencialesInvalidas() {
        return new ExcepcionDeNegocio(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                "El correo o la contraseña no son correctos.");
    }
}
