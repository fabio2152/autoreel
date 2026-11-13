package com.autoreel.video.dominio;

import java.util.List;

import com.autoreel.nicho.pasteleria.PaquetePasteleria;
import tools.jackson.databind.json.JsonMapper;

/** Datos de prueba del guion con el paquete Pastelería real. */
final class Guiones {

    static final PaquetePasteleria PAQUETE = new PaquetePasteleria(JsonMapper.builder().build());

    private Guiones() {
    }

    static DatosDelGuion datos() {
        return datos("PRODUCTO_ESTRELLA", "Producto estrella", "Torta Tres Leches", "Fresas frescas de Huaral", null,
                List.of("dieta"));
    }

    static DatosDelGuion datos(String tipoVideo, String tipoVideoTexto, String producto, String especial,
            String precio, List<String> palabrasProhibidas) {
        return new DatosDelGuion(tipoVideo, tipoVideoTexto, producto, "Bizcocho de tres leches con fresas frescas.",
                especial, precio, "Pedidos por WhatsApp", "Pastelería Dulce Encanto", "CASERO", "Casero",
                "Tortas personalizadas", "Familias", "Lima", "Miraflores", "Recetas de la abuela",
                "Lunes a sábado de 9 a 8", "987654321", List.of("TIENDA", "WHATSAPP"), palabrasProhibidas);
    }

    static GuionPropuesto valido() {
        return guion("Un antojo de fresas frescas", "Torta Tres Leches de casa", "Cada porción con fresas de Huaral",
                "Nuestra Torta Tres Leches lleva fresas frescas de Huaral. Te esperamos en Miraflores.",
                List.of("#pasteleria", "#tortas", "#tresleches", "#postres", "#miraflores"));
    }

    /** Cierre sin precio, con la frase gancho y el distrito del negocio. */
    static final GuionPropuesto.CierrePropuesto CIERRE = new GuionPropuesto.CierrePropuesto(null,
            "Hecha con cariño para ti", null);

    static GuionPropuesto guion(String gancho, String producto, String relleno, String descripcion,
            List<String> hashtags) {
        return new GuionPropuesto(List.of(
                new GuionPropuesto.PlanoPropuesto(1, "ACERCAMIENTO_LENTO", gancho),
                new GuionPropuesto.PlanoPropuesto(2, "ORBITA_FRONTAL", producto),
                new GuionPropuesto.PlanoPropuesto(3, "LUZ_CALIDA", relleno)), CIERRE, descripcion, hashtags);
    }

    /** El guion válido con otro cierre. */
    static GuionPropuesto conCierre(GuionPropuesto.CierrePropuesto cierre) {
        GuionPropuesto valido = valido();
        return new GuionPropuesto(valido.planos(), cierre, valido.descripcion(), valido.hashtags());
    }
}
