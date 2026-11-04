package com.autoreel.auth.aplicacion;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import com.autoreel.auth.dominio.Usuario;
import org.springframework.stereotype.Component;

/**
 * Cuenta los intentos fallidos con correos que no existen, para que se bloqueen igual que una cuenta real
 * (RN-08) y la respuesta no delate qué correos están registrados. Vive en memoria: un reinicio lo vacía,
 * lo que no afecta a las cuentas reales, cuyo bloqueo se guarda en la base de datos.
 */
@Component
class IntentosDeCorreosDesconocidos {

    /** Tope para que una lluvia de correos inventados no llene la memoria. */
    private static final int MAXIMO_REGISTROS = 10_000;

    private record Registro(int intentos, Instant bloqueadoHasta, Instant expira) {
    }

    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();

    boolean estaBloqueado(String correo, Instant ahora) {
        Registro registro = registros.get(correo);
        return registro != null && registro.bloqueadoHasta() != null && registro.bloqueadoHasta().isAfter(ahora);
    }

    void registrarFallo(String correo, Instant ahora) {
        if (registros.size() >= MAXIMO_REGISTROS) {
            registros.values().removeIf(registro -> registro.expira().isBefore(ahora));
        }
        if (registros.size() >= MAXIMO_REGISTROS) {
            return;
        }
        Instant expira = ahora.plus(Usuario.DURACION_BLOQUEO);
        registros.compute(correo, (clave, anterior) -> {
            int intentos = (anterior == null || anterior.expira().isBefore(ahora) ? 0 : anterior.intentos()) + 1;
            if (intentos >= Usuario.MAXIMO_INTENTOS) {
                return new Registro(0, expira, expira);
            }
            return new Registro(intentos, null, expira);
        });
    }
}
