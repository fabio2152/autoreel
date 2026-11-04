package com.autoreel.video.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.video.dominio.ConsumoDiario;
import com.autoreel.video.infraestructura.ConsumoDiarioRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Videos del día (RN-26, RN-27). El día es el calendario de Lima (el {@link Clock} de la aplicación) y los videos
 * no usados no se acumulan.
 */
@Service
public class ServicioConsumo {

    /** @param usados videos aprobados hoy; {@code porDia} los que permite el plan. */
    public record ConsumoDeHoy(int usados, int porDia) {

        public boolean puedeAprobar() {
            return usados < porDia;
        }
    }

    private final ConsumoDiarioRepositorio consumos;
    private final Clock reloj;

    public ServicioConsumo(ConsumoDiarioRepositorio consumos, Clock reloj) {
        this.consumos = consumos;
        this.reloj = reloj;
    }

    /**
     * Consume un video del día con la fila bloqueada, para que dos aprobaciones a la vez no pasen el límite.
     *
     * @return el día consumido.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDate consumir(Long negocioId, int videosPorDia) {
        LocalDate hoy = LocalDate.now(reloj);
        ConsumoDiario consumo = bloquear(negocioId, hoy);
        if (consumo.getVideosGenerados() >= videosPorDia) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "VIDEOS_DEL_DIA_AGOTADOS",
                    videosPorDia == 1 ? "Ya usaste tu video de hoy. Mañana tienes uno nuevo."
                            : "Ya usaste tus " + videosPorDia + " videos de hoy. Mañana tienes otros nuevos.");
        }
        consumo.sumar();
        return hoy;
    }

    /** RN-27: devuelve un video en el día en que ocurre el error (decisión del usuario). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void devolver(Long negocioId) {
        bloquear(negocioId, LocalDate.now(reloj)).restar();
    }

    @Transactional(readOnly = true)
    public ConsumoDeHoy deHoy(Long negocioId, int videosPorDia) {
        int usados = consumos.findByNegocioIdAndFecha(negocioId, LocalDate.now(reloj))
                .map(ConsumoDiario::getVideosGenerados).orElse(0);
        return new ConsumoDeHoy(usados, videosPorDia);
    }

    /** Videos generados en el mes de Lima en curso (panel del administrador, RF-02 y RF-05). */
    @Transactional(readOnly = true)
    public long delMes(Long negocioId) {
        YearMonth mes = YearMonth.now(reloj);
        return consumos.sumar(negocioId, mes.atDay(1), mes.atEndOfMonth());
    }

    private ConsumoDiario bloquear(Long negocioId, LocalDate dia) {
        consumos.crearSiFalta(negocioId, dia);
        return consumos.bloquear(negocioId, dia).orElseThrow();
    }
}
