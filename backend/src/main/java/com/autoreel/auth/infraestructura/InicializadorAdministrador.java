package com.autoreel.auth.infraestructura;

import java.time.Clock;
import java.util.Arrays;

import com.autoreel.auth.dominio.ReglasDeContrasena;
import com.autoreel.auth.dominio.Rol;
import com.autoreel.auth.dominio.Usuario;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sección 16: crea el administrador con el correo y la contraseña de las variables ADMIN_EMAIL y ADMIN_PASSWORD.
 * Solo actúa si todavía no existe ningún administrador; nunca sobrescribe uno existente.
 */
@Component
public class InicializadorAdministrador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorAdministrador.class);

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final Clock reloj;
    private final Environment entorno;
    private final String correo;
    private final String contrasena;

    public InicializadorAdministrador(UsuarioRepositorio usuarios, PasswordEncoder codificador, Clock reloj,
            Environment entorno, @Value("${autoreel.admin.correo:}") String correo,
            @Value("${autoreel.admin.contrasena:}") String contrasena) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.reloj = reloj;
        this.entorno = entorno;
        this.correo = correo;
        this.contrasena = contrasena;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) {
        if (usuarios.existsByRol(Rol.ADMIN)) {
            return;
        }
        if (correo.isBlank() || contrasena.isBlank()) {
            String aviso = "No existe un administrador y faltan las variables ADMIN_EMAIL y ADMIN_PASSWORD.";
            if (Arrays.asList(entorno.getActiveProfiles()).contains("dev")) {
                log.warn(aviso);
                return;
            }
            throw new IllegalStateException(aviso);
        }
        try {
            ReglasDeContrasena.validar(contrasena, contrasena);
        } catch (ExcepcionDeNegocio e) {
            throw new IllegalStateException("ADMIN_PASSWORD no cumple las reglas de contraseña: " + e.getMessage());
        }
        Usuario admin = usuarios.save(Usuario.administrador(correo, codificador.encode(contrasena), reloj.instant()));
        log.info("Administrador inicial creado (id={})", admin.getId());
    }
}
