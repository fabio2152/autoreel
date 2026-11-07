package com.autoreel.nicho.dominio;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Guion del chat del video del día (sección 7.3, RN-16): preguntas fijas, en orden, definidas por el paquete de
 * nicho. El chat no usa IA; solo reúne la información.
 */
public record GuionChat(int minimoCaracteres, int maximoCaracteres, List<String> respuestasNegativas,
        Mensajes mensajes, List<PasoChat> pasos) {

    /** Mensajes fijos que el sistema muestra en situaciones especiales. */
    public record Mensajes(String pregunta, String muyCorta, String muyLarga, String precioObligatorio,
            String productoNoDisponible, String catalogoVacio, String cierre, String aviso) {
    }

    public enum TipoPaso {
        /** Respuesta con un botón; no se agrupa. */
        BOTONES,
        /** Elegir un producto del catálogo o subir uno nuevo. */
        PRODUCTO,
        /** Respuesta libre: se agrupa (RN-17) y se revisa (RN-18). */
        TEXTO
    }

    public enum Sugerencia {
        /** Ofrece la descripción del producto elegido como punto de partida. */
        DESCRIPCION_PRODUCTO
    }

    /** La pregunta es obligatoria si otra respuesta tiene cierto valor (RN-20: precio en una Promoción). */
    public record Condicion(String paso, String valor) {
    }

    /**
     * @param omitible    se puede responder "No, gracias" sin escribir (solo si no es obligatoria en ese momento).
     * @param ejemplo     ejemplo corto de respuesta que se muestra bajo la caja de texto (2026-11-06).
     * @param opcionLibre en un paso de botones, la opción que se usa si el usuario escribe en lugar de tocar un botón:
     *                    su texto se guarda aparte ({@code <id>Libre}) y llega al guion como contexto (2026-11-06).
     */
    public record PasoChat(String id, TipoPaso tipo, String texto, boolean obligatoria, List<Opcion> opciones,
            Condicion obligatoriaSi, boolean omitible, Sugerencia sugerencia, String ejemplo, String opcionLibre) {

        public PasoChat {
            opciones = opciones == null ? List.of() : List.copyOf(opciones);
        }

        public boolean esObligatoria(Map<String, Object> respuestas) {
            return obligatoria || (obligatoriaSi != null
                    && obligatoriaSi.valor().equals(respuestas.get(obligatoriaSi.paso())));
        }

        public Optional<Opcion> opcion(String valor) {
            return opciones.stream().filter(opcion -> opcion.valor().equals(valor)).findFirst();
        }

        /** Clave de la respuesta escrita en un paso de botones. */
        public String claveLibre() {
            return id + "Libre";
        }

        public boolean admiteTexto() {
            return tipo == TipoPaso.TEXTO || (tipo == TipoPaso.BOTONES && opcionLibre != null);
        }
    }

    public GuionChat {
        respuestasNegativas = List.copyOf(respuestasNegativas);
        pasos = List.copyOf(pasos);
    }

    public PasoChat paso(String id) {
        return pasos.stream().filter(paso -> paso.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Paso desconocido: " + id));
    }

    /** El paso que sigue a {@code id}, o vacío si era el último. */
    public Optional<PasoChat> siguiente(String id) {
        int indice = pasos.indexOf(paso(id));
        return indice + 1 < pasos.size() ? Optional.of(pasos.get(indice + 1)) : Optional.empty();
    }

    /** "No", "nada", "sin precio"…: se comparan en minúsculas y sin tildes, espacios ni puntuación. */
    public boolean esNegativa(String texto) {
        String limpio = normalizar(texto);
        return respuestasNegativas.stream().map(GuionChat::normalizar).anyMatch(limpio::equals);
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}-]", "");
    }

    /** Revisa la forma del guion al arrancar: un error en el archivo no debe llegar a producción. */
    public void validar() {
        exigir(!pasos.isEmpty(), "no hay pasos");
        exigir(minimoCaracteres > 0 && maximoCaracteres > minimoCaracteres, "límites de caracteres inválidos");
        exigir(mensajes != null && List.of(mensajes.pregunta(), mensajes.muyCorta(), mensajes.muyLarga(),
                mensajes.precioObligatorio(), mensajes.productoNoDisponible(), mensajes.catalogoVacio(),
                mensajes.cierre(), mensajes.aviso()).stream().allMatch(m -> m != null && !m.isBlank()),
                "faltan mensajes fijos");
        exigir(pasos.stream().filter(p -> p.tipo() == TipoPaso.PRODUCTO).count() == 1,
                "debe haber exactamente un paso PRODUCTO");
        for (int i = 0; i < pasos.size(); i++) {
            PasoChat paso = pasos.get(i);
            exigir(paso.id() != null && !paso.id().isBlank() && paso.texto() != null && !paso.texto().isBlank(),
                    "paso sin id o sin texto");
            final int posicion = i;
            exigir(pasos.stream().filter(p -> p.id().equals(paso.id())).count() == 1, "paso repetido: " + paso.id());
            exigir(paso.tipo() != TipoPaso.BOTONES || !paso.opciones().isEmpty(), paso.id() + " no tiene opciones");
            exigir(paso.opciones().stream().map(Opcion::valor).distinct().count() == paso.opciones().size(),
                    paso.id() + " tiene opciones repetidas");
            exigir(paso.sugerencia() == null || paso.tipo() == TipoPaso.TEXTO,
                    "la sugerencia de " + paso.id() + " solo vale en pasos de texto");
            exigir(!paso.omitible() || paso.tipo() == TipoPaso.TEXTO, paso.id() + ": solo un texto puede omitirse");
            exigir(paso.opcionLibre() == null
                    || (paso.tipo() == TipoPaso.BOTONES && paso.opcion(paso.opcionLibre()).isPresent()),
                    "la opción libre de " + paso.id() + " debe ser una de sus opciones");
            if (paso.obligatoriaSi() != null) {
                var anterior = pasos.subList(0, posicion).stream()
                        .filter(p -> p.id().equals(paso.obligatoriaSi().paso())).findFirst();
                exigir(anterior.isPresent(), "la condición de " + paso.id() + " no apunta a un paso anterior");
                exigir(anterior.get().opcion(paso.obligatoriaSi().valor()).isPresent(),
                        "la condición de " + paso.id() + " usa un valor que no existe");
            }
        }
    }

    private static void exigir(boolean condicion, String problema) {
        if (!condicion) {
            throw new IllegalStateException("Guion del chat inválido: " + problema);
        }
    }
}
