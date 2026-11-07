package com.autoreel.costos.api;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.costos.aplicacion.ServicioCostos;
import com.autoreel.costos.aplicacion.ServicioCostos.CostosDelMes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-07: costo estimado de IA por negocio y por mes ({@code mes=2026-11}; por defecto, el mes en curso de Lima). */
@RestController
@RequestMapping("/api/admin/costos")
public class CostoController {

    private final ServicioCostos servicio;

    public CostoController(ServicioCostos servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public CostosDelMes delMes(@RequestParam(required = false) String mes) {
        YearMonth periodo;
        try {
            periodo = mes == null || mes.isBlank() ? servicio.mesActual() : YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new ExcepcionDeNegocio("MES_INVALIDO", "El mes debe tener el formato AAAA-MM.");
        }
        return servicio.delMes(periodo);
    }
}
