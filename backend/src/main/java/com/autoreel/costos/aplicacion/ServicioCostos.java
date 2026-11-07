package com.autoreel.costos.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.costos.dominio.RegistroCosto;
import com.autoreel.costos.infraestructura.RegistroCostoRepositorio;
import com.autoreel.costos.infraestructura.SumaDeCosto;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.UnidadCosto;
import com.autoreel.negocio.dominio.Negocio;
import com.autoreel.negocio.infraestructura.NegocioRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de lo que cuesta cada llamada a un proveedor de IA (RN-32) y su resumen por negocio y por mes (RF-07). El
 * mes es el calendario de Lima.
 */
@Service
public class ServicioCostos {

    /** @param videos videos con algún costo en el período. */
    public record Costo(BigDecimal guion, BigDecimal clips, BigDecimal total, long videos) {

        static final Costo CERO = new Costo(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    public record CostoDeNegocio(Long negocioId, String nombre, Costo costo) {
    }

    public record CostosDelMes(YearMonth mes, Costo total, List<CostoDeNegocio> negocios) {
    }

    private final RegistroCostoRepositorio registros;
    private final NegocioRepositorio negocios;
    private final Clock reloj;

    public ServicioCostos(RegistroCostoRepositorio registros, NegocioRepositorio negocios, Clock reloj) {
        this.registros = registros;
        this.negocios = negocios;
        this.reloj = reloj;
    }

    /**
     * Registra una llamada y devuelve su costo. {@code cantidad} son solicitudes o segundos, según la unidad del
     * modelo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal registrar(Long negocioId, Long videoId, ModeloIA modelo, ConceptoCosto concepto,
            BigDecimal cantidad) {
        BigDecimal costo = modelo.getCostoReferencial().multiply(cantidad);
        registros.save(new RegistroCosto(negocioId, videoId, modelo.getProveedor(), concepto, cantidad, costo,
                reloj.instant()));
        return costo;
    }

    /** Una solicitud de un modelo que cobra por solicitud (el guion). */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal registrarSolicitud(Long negocioId, Long videoId, ModeloIA modelo, ConceptoCosto concepto) {
        if (modelo.getUnidadCosto() != UnidadCosto.POR_SOLICITUD) {
            throw new IllegalArgumentException("El modelo " + modelo.getNombre() + " no cobra por solicitud");
        }
        return registrar(negocioId, videoId, modelo, concepto, BigDecimal.ONE);
    }

    public YearMonth mesActual() {
        return YearMonth.now(reloj);
    }

    /** RF-07: costo estimado de IA de cada negocio en el mes, del más caro al más barato. */
    @Transactional(readOnly = true)
    public CostosDelMes delMes(YearMonth mes) {
        Map<Long, List<SumaDeCosto>> porNegocio = registros.sumar(inicio(mes), inicio(mes.plusMonths(1))).stream()
                .collect(Collectors.groupingBy(SumaDeCosto::negocioId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, String> nombres = negocios.findAllById(porNegocio.keySet()).stream()
                .collect(Collectors.toMap(Negocio::getId, Negocio::getNombre));
        List<CostoDeNegocio> filas = porNegocio.entrySet().stream()
                .map(entrada -> new CostoDeNegocio(entrada.getKey(), nombres.getOrDefault(entrada.getKey(), "—"),
                        sumar(entrada.getValue())))
                .sorted(Comparator.comparing((CostoDeNegocio fila) -> fila.costo().total()).reversed())
                .toList();
        Costo total = filas.stream().map(CostoDeNegocio::costo).reduce(Costo.CERO, ServicioCostos::juntar);
        return new CostosDelMes(mes, total, filas);
    }

    /** RF-07 y V32: costo estimado de IA del negocio en el mes. */
    @Transactional(readOnly = true)
    public Costo deNegocio(Long negocioId, YearMonth mes) {
        return sumar(registros.sumarDeNegocio(negocioId, inicio(mes), inicio(mes.plusMonths(1))));
    }

    private Instant inicio(YearMonth mes) {
        ZoneId zona = reloj.getZone();
        return mes.atDay(1).atStartOfDay(zona).toInstant();
    }

    private static Costo sumar(List<SumaDeCosto> sumas) {
        Map<ConceptoCosto, SumaDeCosto> porConcepto = sumas.stream()
                .collect(Collectors.toMap(SumaDeCosto::concepto, Function.identity()));
        BigDecimal guion = total(porConcepto.get(ConceptoCosto.GUION));
        BigDecimal clips = total(porConcepto.get(ConceptoCosto.CLIP));
        BigDecimal todo = sumas.stream().map(SumaDeCosto::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        long videos = sumas.stream().mapToLong(suma -> suma.videos() == null ? 0 : suma.videos()).max().orElse(0);
        return new Costo(guion, clips, todo, videos);
    }

    private static Costo juntar(Costo a, Costo b) {
        return new Costo(a.guion().add(b.guion()), a.clips().add(b.clips()), a.total().add(b.total()),
                a.videos() + b.videos());
    }

    private static BigDecimal total(SumaDeCosto suma) {
        return suma == null ? BigDecimal.ZERO : suma.total();
    }
}
