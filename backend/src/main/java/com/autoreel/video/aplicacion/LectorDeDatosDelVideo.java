package com.autoreel.video.aplicacion;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.autoreel.catalogo.aplicacion.ServicioCatalogo;
import com.autoreel.catalogo.dominio.Producto;
import com.autoreel.chat.dominio.Conversacion;
import com.autoreel.chat.infraestructura.ConversacionRepositorio;
import com.autoreel.negocio.dominio.PerfilNegocio;
import com.autoreel.negocio.infraestructura.PerfilNegocioRepositorio;
import com.autoreel.nicho.dominio.Opcion;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.Pregunta;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.video.dominio.DatosDelGuion;
import com.autoreel.video.dominio.Video;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reúne el perfil, el producto y las respuestas del chat de un video. Se llama dentro de una transacción. */
@Component
class LectorDeDatosDelVideo {

    record DatosDelVideo(PaqueteNicho paquete, PerfilNegocio perfil, Producto producto, DatosDelGuion guion) {
    }

    private final ConversacionRepositorio conversaciones;
    private final PerfilNegocioRepositorio perfiles;
    private final ServicioCatalogo catalogo;
    private final RegistroDeNichos nichos;

    LectorDeDatosDelVideo(ConversacionRepositorio conversaciones, PerfilNegocioRepositorio perfiles,
            ServicioCatalogo catalogo, RegistroDeNichos nichos) {
        this.conversaciones = conversaciones;
        this.perfiles = perfiles;
        this.catalogo = catalogo;
        this.nichos = nichos;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    DatosDelVideo leer(Video video) {
        Conversacion conversacion = conversaciones.findById(video.getConversacionId()).orElseThrow();
        PerfilNegocio perfil = perfiles.buscarPorNegocio(video.getNegocioId()).orElseThrow();
        PaqueteNicho paquete = nichos.de(perfil.getNegocio().getNicho());
        Producto producto = catalogo.elegidoParaVideo(video.getNegocioId(), video.getProductoId()).orElseThrow();
        Map<String, Object> chat = conversacion.getRespuestas();
        Map<String, Object> onboarding = perfil.getRespuestas();
        var pasoTipo = paquete.guionDelChat().paso(RespuestasDelChat.TIPO_VIDEO);
        // Si escribió el tipo de video en lugar de elegir un botón, el guion recibe sus palabras como contexto.
        String tipoVideoTexto = chat.get(pasoTipo.claveLibre()) instanceof String libre
                ? pasoTipo.opcion(video.getTipoVideo()).map(Opcion::texto).orElse(video.getTipoVideo())
                        + ". Lo que pidió el negocio: " + libre
                : pasoTipo.opcion(video.getTipoVideo()).map(Opcion::texto).orElse(video.getTipoVideo());
        String estiloTexto = paquete.estilos().stream().filter(opcion -> opcion.valor().equals(perfil.getEstilo()))
                .map(Opcion::texto).findFirst().orElse(perfil.getEstilo());
        DatosDelGuion datos = new DatosDelGuion(video.getTipoVideo(), tipoVideoTexto, producto.getNombre(),
                producto.getDescripcion(), texto(chat.get(RespuestasDelChat.ESPECIAL)),
                texto(chat.get(RespuestasDelChat.PRECIO)), texto(chat.get(RespuestasDelChat.EXTRA)),
                perfil.getNegocio().getNombre(), perfil.getEstilo(), estiloTexto,
                legible(paquete, CamposDelPerfil.ESPECIALIDAD, onboarding),
                legible(paquete, CamposDelPerfil.PUBLICO, onboarding),
                texto(onboarding.get(CamposDelPerfil.CIUDAD)), texto(onboarding.get(CamposDelPerfil.DISTRITO)),
                texto(onboarding.get(CamposDelPerfil.DISTINTIVO)), texto(onboarding.get(CamposDelPerfil.HORARIO)),
                texto(onboarding.get(CamposDelPerfil.WHATSAPP)), lista(onboarding.get(CamposDelPerfil.CANALES)),
                DatosDelGuion.separarPalabrasProhibidas(texto(onboarding.get(CamposDelPerfil.PALABRAS_PROHIBIDAS))));
        return new DatosDelVideo(paquete, perfil, producto, datos);
    }

    /** Las opciones elegidas en el onboarding, como las vio el usuario ("Familias, Jóvenes"). */
    private static String legible(PaqueteNicho paquete, String preguntaId, Map<String, Object> respuestas) {
        List<Opcion> opciones = paquete.preguntasOnboarding().stream().filter(p -> p.id().equals(preguntaId))
                .findFirst().map(Pregunta::opciones).orElse(List.of());
        return String.join(", ", lista(respuestas.get(preguntaId)).stream()
                .map(valor -> opciones.stream().filter(o -> o.valor().equals(valor)).map(Opcion::texto).findFirst()
                        .orElse(valor))
                .toList());
    }

    private static List<String> lista(Object valor) {
        if (valor instanceof Collection<?> valores) {
            return valores.stream().map(String::valueOf).toList();
        }
        return valor == null ? List.of() : List.of(String.valueOf(valor));
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }
}
