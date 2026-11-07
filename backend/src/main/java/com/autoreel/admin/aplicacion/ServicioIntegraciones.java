package com.autoreel.admin.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.autoreel.admin.api.IntegracionDtos.CredencialVista;
import com.autoreel.admin.api.IntegracionDtos.Integraciones;
import com.autoreel.admin.api.IntegracionDtos.ModeloVista;
import com.autoreel.admin.api.IntegracionDtos.ResultadoPrueba;
import com.autoreel.admin.api.IntegracionDtos.SolicitudCredencial;
import com.autoreel.admin.api.IntegracionDtos.SolicitudModelo;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.facturacion.infraestructura.PlanRepositorio;
import com.autoreel.facturacion.infraestructura.SuscripcionRepositorio;
import com.autoreel.ia.aplicacion.FabricaDeProveedores;
import com.autoreel.ia.dominio.AlmacenDeSecretos;
import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import com.autoreel.ia.dominio.TipoModelo;
import com.autoreel.ia.infraestructura.ModeloIARepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ajustes → Integraciones (RF-11, sección 11.5): modelos de IA, prueba de conexión y credenciales. Las credenciales se
 * pegan en el panel pero se guardan fuera de la base de datos y nunca se vuelven a mostrar (ADR 0018).
 */
@Service
public class ServicioIntegraciones {

    private static final Logger log = LoggerFactory.getLogger(ServicioIntegraciones.class);

    private final ModeloIARepositorio modelos;
    private final PlanRepositorio planes;
    private final SuscripcionRepositorio suscripciones;
    private final FabricaDeProveedores proveedores;
    private final ProveedorDeSecretos secretos;
    private final AlmacenDeSecretos almacen;
    private final ObjectMapper json;
    private final Clock reloj;

    public ServicioIntegraciones(ModeloIARepositorio modelos, PlanRepositorio planes,
            SuscripcionRepositorio suscripciones, FabricaDeProveedores proveedores, ProveedorDeSecretos secretos,
            AlmacenDeSecretos almacen, ObjectMapper json, Clock reloj) {
        this.modelos = modelos;
        this.planes = planes;
        this.suscripciones = suscripciones;
        this.proveedores = proveedores;
        this.secretos = secretos;
        this.almacen = almacen;
        this.json = json;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Integraciones listar() {
        List<ModeloIA> todos = modelos.findAll(org.springframework.data.domain.Sort.by("tipo", "id"));
        Map<String, List<String>> modelosPorSecreto = new TreeMap<>();
        for (ModeloIA modelo : todos) {
            modelosPorSecreto.computeIfAbsent(modelo.getNombreSecreto(), nombre -> new java.util.ArrayList<>())
                    .add(modelo.getNombre());
        }
        List<CredencialVista> credenciales = modelosPorSecreto.entrySet().stream()
                .map(entrada -> credencial(entrada.getKey(), entrada.getValue())).toList();
        Map<TipoModelo, Set<String>> adaptadores = new EnumMap<>(TipoModelo.class);
        for (TipoModelo tipo : TipoModelo.values()) {
            adaptadores.put(tipo, proveedores.adaptadores(tipo));
        }
        Map<Long, List<String>> planesPorModelo = planesPorModelo();
        return new Integraciones(todos.stream().map(modelo -> vista(modelo, planesPorModelo)).toList(), credenciales,
                adaptadores);
    }

    /** RN-56: solo con un adaptador que exista en el código. Nace inactivo. */
    @Transactional
    public ModeloVista crear(SolicitudModelo solicitud) {
        if (solicitud.tipo() == null) {
            throw new ExcepcionDeNegocio("TIPO_REQUERIDO", "Elige si el modelo es de texto o de video.");
        }
        exigirAdaptador(solicitud.tipo(), solicitud.adaptador());
        exigirUnico(solicitud, -1L);
        ModeloIA modelo = modelos.save(new ModeloIA(solicitud.nombre().trim(), proveedor(solicitud),
                solicitud.tipo(), solicitud.adaptador(), solicitud.identificadorModelo().trim(),
                parametros(solicitud), solicitud.costoReferencial(), solicitud.nombreSecreto().trim()));
        return vista(modelo, planesPorModelo());
    }

    @Transactional
    public ModeloVista actualizar(Long id, SolicitudModelo solicitud) {
        ModeloIA modelo = modelo(id);
        exigirAdaptador(modelo.getTipo(), solicitud.adaptador());
        exigirUnico(solicitud, id);
        modelo.actualizar(solicitud.nombre().trim(), proveedor(solicitud), solicitud.adaptador(),
                solicitud.identificadorModelo().trim(), parametros(solicitud), solicitud.costoReferencial(),
                solicitud.nombreSecreto().trim());
        return vista(modelo, planesPorModelo());
    }

    @Transactional
    public ModeloVista activar(Long id) {
        ModeloIA modelo = modelo(id);
        exigirAdaptador(modelo.getTipo(), modelo.getAdaptador());
        if (secretos.obtener(modelo.getNombreSecreto()).isEmpty()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "SIN_CREDENCIAL", "Configura la credencial "
                    + modelo.getNombreSecreto() + " antes de activar el modelo.");
        }
        modelo.activar();
        return vista(modelo, planesPorModelo());
    }

    /** RN-48: no se desactiva un modelo que use un plan activo o un plan con negocios. */
    @Transactional
    public ModeloVista desactivar(Long id) {
        ModeloIA modelo = modelo(id);
        List<String> enUso = planes.queUsanModelo(id).stream()
                .filter(plan -> plan.isActivo() || suscripciones.countByPlanId(plan.getId()) > 0)
                .map(Plan::getNombre).toList();
        if (!enUso.isEmpty()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "MODELO_EN_USO", "El plan " + String.join(", ", enUso)
                    + " usa este modelo. Asígnale otro modelo antes de desactivarlo.");
        }
        modelo.desactivar();
        return vista(modelo, planesPorModelo());
    }

    /** RF-11 y V37: "Probar conexión" informa si la credencial y el modelo funcionan, sin generar contenido. */
    @Transactional
    public ResultadoPrueba probar(Long id) {
        ModeloIA modelo = modelo(id);
        Instant ahora = reloj.instant();
        ResultadoPrueba resultado = intentar(modelo, ahora);
        modelo.registrarPrueba(ahora, resultado.exitosa(), resultado.mensaje());
        return resultado;
    }

    @Transactional(readOnly = true)
    public CredencialVista guardarCredencial(SolicitudCredencial solicitud) {
        String nombre = solicitud.nombre().trim();
        List<String> usan = modelosQueUsan(nombre);
        String valor = solicitud.valor().strip();
        if (valor.startsWith("{")) {
            try {
                json.readTree(valor);
            } catch (RuntimeException e) {
                throw new ExcepcionDeNegocio("CREDENCIAL_INVALIDA",
                        "La credencial parece un JSON pero no es válido. Pega el archivo completo de la cuenta de servicio.");
            }
        }
        almacen.guardar(nombre, valor);
        log.info("El administrador cambió la credencial {}", nombre);
        return credencial(nombre, usan);
    }

    @Transactional(readOnly = true)
    public CredencialVista borrarCredencial(String nombre) {
        List<String> usan = modelosQueUsan(nombre);
        almacen.borrar(nombre);
        return credencial(nombre, usan);
    }

    private ResultadoPrueba intentar(ModeloIA modelo, Instant ahora) {
        if (!proveedores.adaptadores(modelo.getTipo()).contains(modelo.getAdaptador())) {
            return new ResultadoPrueba(false, "El adaptador " + modelo.getAdaptador() + " no existe en el código.",
                    ahora);
        }
        var credencial = secretos.obtener(modelo.getNombreSecreto());
        if (credencial.isEmpty()) {
            return new ResultadoPrueba(false,
                    "No hay credencial configurada para " + modelo.getNombreSecreto() + ".", ahora);
        }
        try {
            proveedores.probar(modelo, credencial.get());
            return new ResultadoPrueba(true, "La conexión funciona.", ahora);
        } catch (ErrorDeProveedor e) {
            String motivo = switch (e.getTipo()) {
                case CREDENCIAL_INVALIDA -> "La credencial no funciona. ";
                case CONFIGURACION_INVALIDA -> "El modelo está mal configurado. ";
                case NO_DISPONIBLE -> "El proveedor no está disponible. ";
            };
            return new ResultadoPrueba(false, motivo + e.getMessage() + ".", ahora);
        }
    }

    private ModeloVista vista(ModeloIA modelo, Map<Long, List<String>> planesPorModelo) {
        return new ModeloVista(modelo.getId(), modelo.getNombre(), modelo.getProveedor(), modelo.getTipo(),
                modelo.getAdaptador(), modelo.getIdentificadorModelo(), modelo.getParametros(),
                modelo.getCostoReferencial(), modelo.getUnidadCosto(), modelo.getNombreSecreto(), modelo.getEstado(),
                modelo.getUltimaPrueba(), modelo.getUltimaPruebaExitosa(), modelo.getUltimaPruebaMensaje(),
                secretos.obtener(modelo.getNombreSecreto()).isPresent(),
                proveedores.adaptadores(modelo.getTipo()).contains(modelo.getAdaptador()),
                planesPorModelo.getOrDefault(modelo.getId(), List.of()));
    }

    private CredencialVista credencial(String nombre, List<String> modelosQueLaUsan) {
        return new CredencialVista(nombre, almacen.origen(nombre),
                secretos.obtener(nombre).map(this::describir).orElse(null), modelosQueLaUsan);
    }

    /** Una pista para reconocer la credencial sin mostrarla. */
    private String describir(String valor) {
        String limpio = valor.strip();
        if (limpio.startsWith("{")) {
            try {
                JsonNode nodo = json.readTree(limpio);
                String correo = nodo.path("client_email").asString("");
                if (!correo.isBlank()) {
                    return "Cuenta de servicio " + correo;
                }
            } catch (RuntimeException e) {
                return "JSON no válido";
            }
            return "JSON sin cuenta de servicio";
        }
        return limpio.length() >= 12 ? "Termina en …" + limpio.substring(limpio.length() - 4) : "Configurada";
    }

    private Map<Long, List<String>> planesPorModelo() {
        Map<Long, List<String>> resultado = new HashMap<>();
        for (Plan plan : planes.listarConModelos()) {
            resultado.computeIfAbsent(plan.getModeloTexto().getId(), id -> new java.util.ArrayList<>())
                    .add(plan.getNombre());
            resultado.computeIfAbsent(plan.getModeloVideo().getId(), id -> new java.util.ArrayList<>())
                    .add(plan.getNombre());
        }
        return resultado;
    }

    private List<String> modelosQueUsan(String nombreSecreto) {
        List<String> usan = modelos.findAll().stream()
                .filter(modelo -> modelo.getNombreSecreto().equals(nombreSecreto)).map(ModeloIA::getNombre).toList();
        if (usan.isEmpty()) {
            throw ExcepcionDeNegocio.noEncontrado("Ningún modelo usa la credencial " + nombreSecreto + ".");
        }
        return usan;
    }

    private void exigirAdaptador(TipoModelo tipo, String adaptador) {
        if (!proveedores.adaptadores(tipo).contains(adaptador)) {
            throw new ExcepcionDeNegocio("ADAPTADOR_INEXISTENTE", "El adaptador " + adaptador + " no existe para "
                    + (tipo == TipoModelo.TEXTO ? "texto" : "video") + ". Un proveedor nuevo requiere programar su adaptador.");
        }
    }

    private void exigirUnico(SolicitudModelo solicitud, Long excepto) {
        if (modelos.existeAdaptadorEIdentificador(solicitud.adaptador(), solicitud.identificadorModelo().trim(),
                excepto)) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "MODELO_YA_EXISTE",
                    "Ya existe un modelo con ese adaptador e identificador.");
        }
    }

    private ModeloIA modelo(Long id) {
        return modelos.findById(id).orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos ese modelo."));
    }

    private static String proveedor(SolicitudModelo solicitud) {
        return solicitud.proveedor().trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static Map<String, Object> parametros(SolicitudModelo solicitud) {
        return solicitud.parametros() == null ? Map.of() : solicitud.parametros();
    }
}
