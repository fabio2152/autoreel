package com.autoreel.video.aplicacion;

/**
 * Preguntas del onboarding (sección 7.2) que usan el guion y el cierre. Como {@link RespuestasDelChat}, son el
 * contrato entre {@code onboarding.json} y {@code guion.md} de cada paquete de nicho.
 */
final class CamposDelPerfil {

    static final String ESPECIALIDAD = "especialidad";
    static final String PUBLICO = "publico";
    static final String CIUDAD = "ciudad";
    static final String DISTRITO = "distrito";
    static final String CANALES = "canales";
    static final String WHATSAPP = "whatsapp";
    static final String HORARIO = "horario";
    static final String DISTINTIVO = "distintivo";
    static final String PALABRAS_PROHIBIDAS = "palabrasProhibidas";
    /** Opción de {@link #CANALES}: el cierre muestra el número de WhatsApp. */
    static final String CANAL_WHATSAPP = "WHATSAPP";
    static final String WEB = "web";
    /** Opción de {@link #CANALES}: el cierre muestra la página web (2026-11-25). */
    static final String CANAL_WEB = "WEB";

    /** La página web que se muestra en el cierre: solo si el negocio vende por su web. */
    static String paginaWebDelCierre(java.util.Map<String, Object> respuestas) {
        return respuestas.get(CANALES) instanceof java.util.Collection<?> canales && canales.contains(CANAL_WEB)
                && respuestas.get(WEB) instanceof String web && !web.isBlank() ? web : null;
    }

    private CamposDelPerfil() {
    }
}
