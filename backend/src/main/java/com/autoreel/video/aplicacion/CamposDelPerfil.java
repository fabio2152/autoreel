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

    private CamposDelPerfil() {
    }
}
