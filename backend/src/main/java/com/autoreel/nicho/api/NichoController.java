package com.autoreel.nicho.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.autoreel.compartido.seguridad.UsuarioAutenticado;
import com.autoreel.negocio.infraestructura.NegocioRepositorio;
import com.autoreel.nicho.dominio.Opcion;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.Pregunta;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.nicho.dominio.Temporada;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NichoController {

    /** Lo que el frontend necesita del paquete del negocio: estilos, preguntas y temporadas. */
    public record ConfiguracionNicho(String nicho, String nombre, List<Opcion> estilos, List<Pregunta> preguntas,
            List<TemporadaVisible> temporadas, List<String> temporadasVigentes) {
    }

    public record TemporadaVisible(String id, String nombre) {
        static TemporadaVisible de(Temporada temporada) {
            return new TemporadaVisible(temporada.id(), temporada.nombre());
        }
    }

    private final RegistroDeNichos nichos;
    private final NegocioRepositorio negocios;
    private final Clock reloj;

    public NichoController(RegistroDeNichos nichos, NegocioRepositorio negocios, Clock reloj) {
        this.nichos = nichos;
        this.negocios = negocios;
        this.reloj = reloj;
    }

    @GetMapping("/api/nicho/configuracion")
    public ConfiguracionNicho configuracion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        String nicho = negocios.findById(usuario.negocioId())
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos tu negocio."))
                .getNicho();
        PaqueteNicho paquete = nichos.de(nicho);
        LocalDate hoy = LocalDate.now(reloj);
        return new ConfiguracionNicho(paquete.id(), paquete.nombre(), paquete.estilos(), paquete.preguntasOnboarding(),
                paquete.temporadas().stream().map(TemporadaVisible::de).toList(),
                paquete.temporadasVigentes(hoy).stream().map(Temporada::id).toList());
    }
}
