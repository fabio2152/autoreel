package com.autoreel.admin.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.autoreel.ia.dominio.AlmacenDeSecretos;
import com.autoreel.ia.dominio.EstadoModelo;
import com.autoreel.ia.dominio.TipoModelo;
import com.autoreel.ia.dominio.UnidadCosto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Ajustes → Integraciones (RF-11): modelos de IA y sus credenciales. */
public final class IntegracionDtos {

    private IntegracionDtos() {
    }

    /**
     * @param credencialConfigurada si su secreto tiene valor (nunca se muestra el valor, RN-55).
     * @param adaptadorDisponible   si su adaptador existe en el código (RN-56).
     * @param planes                planes que lo usan.
     */
    public record ModeloVista(Long id, String nombre, String proveedor, TipoModelo tipo, String adaptador,
            String identificadorModelo, Map<String, Object> parametros, BigDecimal costoReferencial,
            UnidadCosto unidadCosto, String nombreSecreto, EstadoModelo estado, Instant ultimaPrueba,
            Boolean ultimaPruebaExitosa, String ultimaPruebaMensaje, boolean credencialConfigurada,
            boolean adaptadorDisponible, List<String> planes) {
    }

    /**
     * @param origen      PANEL si se pegó en Ajustes, CONFIGURACION si viene del .env, NINGUNO si falta.
     * @param descripcion pista para reconocerla sin mostrarla: el correo de la cuenta de servicio o los últimos 4
     *                    caracteres de la clave.
     */
    public record CredencialVista(String nombre, AlmacenDeSecretos.Origen origen, String descripcion,
            List<String> modelos) {
    }

    public record Integraciones(List<ModeloVista> modelos, List<CredencialVista> credenciales,
            Map<TipoModelo, Set<String>> adaptadores) {
    }

    /** El tipo solo se usa al crear: después no cambia, porque el modelo puede estar asignado a planes. */
    public record SolicitudModelo(
            @NotBlank(message = "Escribe el nombre visible del modelo.")
            @Size(max = 80, message = "El nombre puede tener como máximo 80 caracteres.") String nombre,
            @NotBlank(message = "Escribe el proveedor (por ejemplo, GOOGLE).")
            @Size(max = 30, message = "El proveedor puede tener como máximo 30 caracteres.") String proveedor,
            TipoModelo tipo,
            @NotBlank(message = "Elige el adaptador.") String adaptador,
            @NotBlank(message = "Escribe el identificador exacto del modelo en el proveedor.")
            @Size(max = 120, message = "El identificador puede tener como máximo 120 caracteres.")
            String identificadorModelo,
            Map<String, Object> parametros,
            @NotNull(message = "Escribe el costo de referencia.")
            @DecimalMin(value = "0", message = "El costo de referencia no puede ser negativo.")
            @Digits(integer = 6, fraction = 6, message = "El costo admite hasta seis decimales.")
            BigDecimal costoReferencial,
            @NotBlank(message = "Escribe el nombre de la credencial (por ejemplo, autoreel/gemini).")
            @Size(max = 120, message = "El nombre de la credencial puede tener como máximo 120 caracteres.")
            @Pattern(regexp = "[a-z0-9][a-z0-9/_.-]*",
                    message = "El nombre de la credencial solo admite minúsculas, números, / _ . y -.")
            String nombreSecreto) {
    }

    public record ResultadoPrueba(boolean exitosa, String mensaje, Instant fecha) {
    }

    public record SolicitudCredencial(
            @NotBlank(message = "Elige la credencial.") String nombre,
            @NotBlank(message = "Pega el valor de la credencial.")
            @Size(max = 20000, message = "La credencial es demasiado larga.") String valor) {
    }
}
