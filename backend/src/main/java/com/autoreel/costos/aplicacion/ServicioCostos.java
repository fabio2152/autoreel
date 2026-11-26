package com.autoreel.costos.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.costos.dominio.RegistroCosto;
import com.autoreel.costos.infraestructura.CostoRegistrado;
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

    /** @param serie costo acumulado al cierre de cada día de {@link CostosDiarios#dias()}, en US$. */
    public record SerieDeNegocio(Long negocioId, String nombre, List<BigDecimal> serie) {
    }

    /** @param dias días del mes con datos (1, 2, ...): el mes en curso llega hasta hoy. */
    public record CostosDiarios(YearMonth mes, List<Integer> dias, List<SerieDeNegocio> negocios) {
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

    /**
     * RF-07: costo acumulado de cada negocio día a día en el mes, para el gráfico del admin. Suma todos los conceptos,
     * así que el último valor de cada serie es el total de la tabla de {@link #delMes}. Los días son de Lima y el mes en
     * curso se corta en hoy.
     */
    @Transactional(readOnly = true)
    public CostosDiarios diarioDelMes(YearMonth mes) {
        LocalDate hoy = LocalDate.now(reloj);
        YearMonth actual = YearMonth.from(hoy);
        int ultimoDia = mes.isAfter(actual) ? 0 : mes.equals(actual) ? hoy.getDayOfMonth() : mes.lengthOfMonth();
        Map<Long, BigDecimal[]> porDia = new LinkedHashMap<>();
        for (CostoRegistrado registro : registros.listarEntre(inicio(mes), inicio(mes.plusMonths(1)))) {
            int dia = registro.fechaHora().atZone(reloj.getZone()).getDayOfMonth();
            if (dia > ultimoDia) {
                continue;
            }
            BigDecimal[] costos = porDia.computeIfAbsent(registro.negocioId(), id -> ceros(ultimoDia));
            costos[dia - 1] = costos[dia - 1].add(registro.costoUsd());
        }
        Map<Long, String> nombres = negocios.findAllById(porDia.keySet()).stream()
                .collect(Collectors.toMap(Negocio::getId, Negocio::getNombre));
        List<SerieDeNegocio> series = porDia.entrySet().stream()
                .map(entrada -> new SerieDeNegocio(entrada.getKey(), nombres.getOrDefault(entrada.getKey(), "—"),
                        acumular(entrada.getValue())))
                .sorted(Comparator.comparing((SerieDeNegocio serie) -> serie.serie().getLast()).reversed())
                .toList();
        return new CostosDiarios(mes, IntStream.rangeClosed(1, ultimoDia).boxed().toList(), series);
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

    private static BigDecimal[] ceros(int dias) {
        BigDecimal[] costos = new BigDecimal[dias];
        Arrays.fill(costos, BigDecimal.ZERO);
        return costos;
    }

    private static List<BigDecimal> acumular(BigDecimal[] porDia) {
        List<BigDecimal> serie = new ArrayList<>(porDia.length);
        BigDecimal acumulado = BigDecimal.ZERO;
        for (BigDecimal costo : porDia) {
            acumulado = acumulado.add(costo);
            serie.add(acumulado);
        }
        return serie;
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
