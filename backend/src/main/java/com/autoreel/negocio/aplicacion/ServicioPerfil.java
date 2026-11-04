package com.autoreel.negocio.aplicacion;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos;
import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos.ImagenGuardada;
import com.autoreel.almacenamiento.dominio.PropositoArchivo;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.negocio.api.Perfil;
import com.autoreel.negocio.dominio.Negocio;
import com.autoreel.negocio.dominio.PerfilNegocio;
import com.autoreel.negocio.infraestructura.PerfilNegocioRepositorio;
import com.autoreel.nicho.dominio.Opcion;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.nicho.dominio.ValidadorDeRespuestas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil y onboarding del negocio (RF-16, RF-17). El asistente guarda cada paso con modificaciones parciales; al
 * terminar, {@link #completarOnboarding} exige todo lo obligatorio (RN-09).
 */
@Service
public class ServicioPerfil {

    public static final String AVISO_LOGO_SIN_TRANSPARENCIA = "LOGO_SIN_TRANSPARENCIA";
    public static final String AVISO_FOTO_LOCAL_HORIZONTAL = "FOTO_LOCAL_HORIZONTAL";
    private static final int LONGITUD_MAXIMA_NOMBRE = 120;
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-f]{6}$");
    private static final Set<String> CAMPOS =
            Set.of("nombre", "logo", "colorPrimario", "colorSecundario", "estilo", "fotoLocal", "respuestas");

    private final PerfilNegocioRepositorio perfiles;
    private final RegistroDeNichos nichos;
    private final ServicioDeArchivos archivos;
    private final Clock reloj;

    public ServicioPerfil(PerfilNegocioRepositorio perfiles, RegistroDeNichos nichos, ServicioDeArchivos archivos,
            Clock reloj) {
        this.perfiles = perfiles;
        this.nichos = nichos;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Perfil obtener(Long negocioId) {
        return vista(perfil(negocioId), List.of());
    }

    /** Respuesta del onboarding con el texto de la pregunta y de las opciones, para el panel de administración. */
    public record RespuestaLegible(String pregunta, String respuesta) {
    }

    public record PerfilParaAdmin(Perfil perfil, List<RespuestaLegible> respuestas) {
    }

    @Transactional(readOnly = true)
    public PerfilParaAdmin paraAdmin(Long negocioId) {
        PerfilNegocio perfil = perfil(negocioId);
        PaqueteNicho paquete = nichos.de(perfil.getNegocio().getNicho());
        List<RespuestaLegible> legibles = paquete.preguntasOnboarding().stream()
                .filter(pregunta -> perfil.getRespuestas().containsKey(pregunta.id()))
                .map(pregunta -> {
                    Object valor = perfil.getRespuestas().get(pregunta.id());
                    String texto = valor instanceof List<?> valores
                            ? valores.stream().map(v -> pregunta.opciones().stream()
                                    .filter(opcion -> opcion.valor().equals(v)).map(Opcion::texto)
                                    .findFirst().orElse(String.valueOf(v)))
                                    .reduce((a, b) -> a + ", " + b).orElse("")
                            : String.valueOf(valor);
                    return new RespuestaLegible(pregunta.texto(), texto);
                })
                .toList();
        return new PerfilParaAdmin(vista(perfil, List.of()), legibles);
    }

    /**
     * Modificación parcial: un campo que no viene no cambia; uno que viene en {@code null} se borra. Las respuestas
     * se fusionan por pregunta ({@code null} borra esa respuesta). Si el onboarding ya terminó, el perfil resultante
     * debe seguir completo.
     */
    @Transactional
    public Perfil actualizar(Long negocioId, Map<String, Object> cambios) {
        PerfilNegocio perfil = perfil(negocioId);
        Negocio negocio = perfil.getNegocio();
        PaqueteNicho paquete = nichos.de(negocio.getNicho());
        List<String> avisos = new ArrayList<>();
        cambios.keySet().stream().filter(campo -> !CAMPOS.contains(campo)).findFirst().ifPresent(campo -> {
            throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA", "No reconocemos el campo \"" + campo + "\".");
        });

        // Primero lo que no son archivos: si algo es inválido, no se llega a confirmar ninguna subida.
        if (cambios.containsKey("nombre")) {
            negocio.cambiarNombre(nombre(cambios.get("nombre")));
        }
        if (cambios.containsKey("colorPrimario") || cambios.containsKey("colorSecundario")) {
            perfil.cambiarColores(
                    cambios.containsKey("colorPrimario") ? color(cambios.get("colorPrimario")) : perfil.getColorPrimario(),
                    cambios.containsKey("colorSecundario") ? color(cambios.get("colorSecundario")) : perfil.getColorSecundario());
        }
        if (cambios.containsKey("estilo")) {
            String estilo = texto(cambios.get("estilo"), "estilo");
            if (estilo != null && !paquete.admiteEstilo(estilo)) {
                throw new ExcepcionDeNegocio("ESTILO_INVALIDO", "Elige uno de los estilos disponibles.");
            }
            perfil.cambiarEstilo(estilo);
        }
        if (cambios.containsKey("respuestas")) {
            perfil.cambiarRespuestas(fusionar(paquete, perfil.getRespuestas(), cambios.get("respuestas")));
        }
        if (cambios.containsKey("logo")) {
            String clave = texto(cambios.get("logo"), "logo");
            if (clave == null) {
                perfil.cambiarLogo(null, false);
            } else if (!clave.equals(perfil.getLogoClave())) {
                ImagenGuardada logo = guardar(negocioId, PropositoArchivo.LOGO, clave);
                perfil.cambiarLogo(logo.clave(), logo.imagen().sinTransparencia());
                if (logo.imagen().sinTransparencia()) {
                    avisos.add(AVISO_LOGO_SIN_TRANSPARENCIA);
                }
            }
        }
        if (cambios.containsKey("fotoLocal")) {
            String clave = texto(cambios.get("fotoLocal"), "fotoLocal");
            if (clave == null) {
                perfil.cambiarFotoLocal(null);
            } else if (!clave.equals(perfil.getFotoLocalClave())) {
                ImagenGuardada foto = guardar(negocioId, PropositoArchivo.FOTO_LOCAL, clave);
                perfil.cambiarFotoLocal(foto.clave());
                if (foto.imagen().horizontal()) {
                    avisos.add(AVISO_FOTO_LOCAL_HORIZONTAL);
                }
            }
        }
        if (negocio.isOnboardingCompleto()) {
            exigirCompleto(perfil, paquete);
        }
        perfil.marcarActualizado(reloj.instant());
        return vista(perfil, avisos);
    }

    @Transactional
    public Perfil completarOnboarding(Long negocioId) {
        PerfilNegocio perfil = perfil(negocioId);
        exigirCompleto(perfil, nichos.de(perfil.getNegocio().getNicho()));
        perfil.getNegocio().completarOnboarding();
        return vista(perfil, List.of());
    }

    /** Crea el perfil vacío de un negocio nuevo. */
    @Transactional
    public PerfilNegocio crearVacio(Negocio negocio) {
        return perfiles.save(new PerfilNegocio(negocio, reloj.instant()));
    }

    private void exigirCompleto(PerfilNegocio perfil, PaqueteNicho paquete) {
        if (perfil.getLogoClave() == null) {
            throw incompleto("Falta tu logo.");
        }
        if (perfil.getColorPrimario() == null || perfil.getColorSecundario() == null) {
            throw incompleto("Faltan tus dos colores de marca.");
        }
        if (perfil.getEstilo() == null) {
            throw incompleto("Falta elegir el estilo de tus videos.");
        }
        ValidadorDeRespuestas.exigirCompletas(paquete.preguntasOnboarding(), perfil.getRespuestas());
    }

    private static Map<String, Object> fusionar(PaqueteNicho paquete, Map<String, Object> actuales, Object nuevas) {
        if (!(nuevas instanceof Map<?, ?> mapa)) {
            throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA", "Las respuestas no tienen un formato válido.");
        }
        Map<String, Object> fusion = new LinkedHashMap<>(actuales);
        Map<String, Object> agregadas = new LinkedHashMap<>();
        mapa.forEach((id, valor) -> {
            if (valor == null) {
                fusion.remove(String.valueOf(id));
            } else {
                agregadas.put(String.valueOf(id), valor);
            }
        });
        Map<String, Object> limpias = ValidadorDeRespuestas.normalizar(paquete.preguntasOnboarding(), agregadas);
        agregadas.keySet().forEach(fusion::remove);
        fusion.putAll(limpias);
        return fusion;
    }

    private ImagenGuardada guardar(Long negocioId, PropositoArchivo proposito, String clave) {
        if (!archivos.esSubida(clave)) {
            throw new ExcepcionDeNegocio("ARCHIVO_INVALIDO", "El archivo no corresponde a tu negocio. Vuelve a subirlo.");
        }
        return archivos.confirmar(negocioId, proposito, clave);
    }

    private static String nombre(Object valor) {
        String nombre = texto(valor, "nombre");
        if (nombre == null || nombre.isEmpty()) {
            throw new ExcepcionDeNegocio("DATOS_INVALIDOS", "Escribe el nombre de tu negocio.");
        }
        if (nombre.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ExcepcionDeNegocio("DATOS_INVALIDOS",
                    "El nombre puede tener como máximo " + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        return nombre;
    }

    private static String color(Object valor) {
        String color = texto(valor, "color");
        if (color == null) {
            return null;
        }
        String normalizado = color.toLowerCase(Locale.ROOT);
        if (!COLOR.matcher(normalizado).matches()) {
            throw new ExcepcionDeNegocio("COLOR_INVALIDO", "Los colores deben tener el formato #RRGGBB.");
        }
        return normalizado;
    }

    private static String texto(Object valor, String campo) {
        if (valor == null) {
            return null;
        }
        if (!(valor instanceof String texto)) {
            throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA", "El campo \"" + campo + "\" debe ser un texto.");
        }
        return texto.strip();
    }

    private PerfilNegocio perfil(Long negocioId) {
        return perfiles.buscarPorNegocio(negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos el perfil de tu negocio."));
    }

    public Perfil vista(PerfilNegocio perfil, List<String> avisos) {
        Negocio negocio = perfil.getNegocio();
        return new Perfil(negocio.getNombre(), negocio.getNicho(), imagen(perfil.getLogoClave()),
                perfil.isLogoSinTransparencia(), perfil.getColorPrimario(), perfil.getColorSecundario(),
                perfil.getEstilo(), imagen(perfil.getFotoLocalClave()), perfil.getRespuestas(),
                negocio.isOnboardingCompleto(), avisos);
    }

    private Perfil.Imagen imagen(String clave) {
        return clave == null ? null : new Perfil.Imagen(clave, archivos.urlDeLectura(clave));
    }

    private static ExcepcionDeNegocio incompleto(String mensaje) {
        return new ExcepcionDeNegocio("PERFIL_INCOMPLETO", mensaje);
    }
}
