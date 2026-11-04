package com.autoreel.video.aplicacion;

import java.util.List;
import java.util.Optional;

import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.PistaDelNicho;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.video.dominio.PistaMusical;
import com.autoreel.video.infraestructura.PistaMusicalRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Biblioteca de música (sección 7.4). Al arrancar, sube al almacenamiento las pistas que traen los paquetes de
 * nicho y registra las que falten; las pistas que agregue el administrador (más adelante) conviven con ellas.
 */
@Component
public class BibliotecaMusical implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BibliotecaMusical.class);

    private final PistaMusicalRepositorio pistas;
    private final RegistroDeNichos nichos;
    private final AlmacenDeArchivos almacen;

    public BibliotecaMusical(PistaMusicalRepositorio pistas, RegistroDeNichos nichos, AlmacenDeArchivos almacen) {
        this.pistas = pistas;
        this.nichos = nichos;
        this.almacen = almacen;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) {
        for (var nicho : nichos.todos()) {
            PaqueteNicho paquete = nichos.de(nicho.id());
            for (PistaDelNicho pista : paquete.pistas()) {
                String clave = "musica/" + nicho.id().toLowerCase() + "/" + pista.archivo();
                if (pistas.existsByClave(clave)) {
                    continue;
                }
                almacen.guardar(clave, paquete.leerPista(pista), "audio/mp4");
                pistas.save(new PistaMusical(nicho.id(), pista.estilo(), pista.nombre(), clave,
                        pista.duracionSegundos(), pista.licencia()));
                log.info("Pista de música registrada: {}", clave);
            }
        }
    }

    /**
     * La pista de un video: una de las activas del estilo, elegida por el id del video para que cada negocio no
     * escuche siempre la misma y recomponer use la misma.
     */
    @Transactional(readOnly = true)
    public Optional<PistaMusical> elegir(String nicho, String estilo, long videoId) {
        List<PistaMusical> delEstilo = pistas.findByNichoAndEstiloAndActivaTrueOrderById(nicho, estilo);
        if (delEstilo.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(delEstilo.get((int) (videoId % delEstilo.size())));
    }
}
