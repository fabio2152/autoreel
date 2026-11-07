package com.autoreel.nicho.pasteleria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.autoreel.nicho.dominio.GuionChat;
import com.autoreel.nicho.dominio.GuionChat.PasoChat;
import com.autoreel.nicho.dominio.Temporada;
import com.autoreel.nicho.dominio.ValidadorDeRespuestas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Temporadas y fechas especiales (sección 7.7) y validación de las respuestas del onboarding (sección 7.1), con los
 * archivos reales del paquete Pastelería.
 */
class PaquetePasteleriaTest {

    private final PaquetePasteleria paquete = new PaquetePasteleria(JsonMapper.builder().build());

    @ParameterizedTest
    @CsvSource({
            "2026-11-30, ''",
            "2026-12-20, NAVIDAD",
            "2026-12-21, VERANO;NAVIDAD",
            "2027-01-15, VERANO",
            "2027-03-20, VERANO",
            "2027-03-21, ''",
            "2027-02-14, VERANO;SAN_VALENTIN",
            "2026-05-10, DIA_DE_LA_MADRE",
            "2026-06-21, DIA_DEL_PADRE;INVIERNO",
            "2026-07-29, INVIERNO;FIESTAS_PATRIAS",
            "2026-10-31, HALLOWEEN_CANCION_CRIOLLA"
    })
    void calculaLasTemporadasVigentesSinContarTodoElAnio(LocalDate fecha, String esperadas) {
        List<String> vigentes = paquete.temporadasVigentes(fecha).stream().map(Temporada::id).sorted().toList();

        assertThat(vigentes).isEqualTo(esperadas.isEmpty() ? List.of()
                : List.of(esperadas.split(";")).stream().sorted().toList());
    }

    @ParameterizedTest
    @CsvSource({
            "2027-01-31, SAN_VALENTIN",
            "2027-02-15, ''",
            "2026-04-26, DIA_DE_LA_MADRE",
            "2026-07-14, FIESTAS_PATRIAS",
            "2026-10-20, HALLOWEEN_CANCION_CRIOLLA",
            "2026-11-20, NAVIDAD"
    })
    void anunciaLasFechasEspecialesDeLosProximos15Dias(LocalDate fecha, String esperada) {
        List<String> proximas = paquete.fechasEspecialesProximas(fecha).stream().map(Temporada::id).toList();

        assertThat(proximas).isEqualTo(esperada.isEmpty() ? List.of() : List.of(esperada));
    }

    @Test
    void unGuionDelChatMalArmadoFallaAlArrancar() {
        GuionChat valido = paquete.guionDelChat();
        PasoChat precio = valido.paso("precio");
        PasoChat conCondicionRota = new PasoChat(precio.id(), precio.tipo(), precio.texto(), false, List.of(),
                new GuionChat.Condicion("tipoVideo", "NO_EXISTE"), true, null, null, null);
        List<PasoChat> pasos = valido.pasos().stream().map(p -> p.id().equals("precio") ? conCondicionRota : p).toList();
        GuionChat roto = new GuionChat(valido.minimoCaracteres(), valido.maximoCaracteres(),
                valido.respuestasNegativas(), valido.mensajes(), pasos);

        assertThatCode(valido::validar).doesNotThrowAnyException();
        assertThatThrownBy(roto::validar).hasMessageContaining("usa un valor que no existe");
    }

    @Test
    void elDiaDeLaMadreEsElSegundoDomingoDeMayo() {
        Temporada madre = paquete.temporadas().stream().filter(t -> t.id().equals("DIA_DE_LA_MADRE")).findFirst()
                .orElseThrow();

        assertThat(madre.proximoInicio(LocalDate.of(2027, 1, 1))).contains(LocalDate.of(2027, 5, 9));
        assertThat(madre.vigenteEn(LocalDate.of(2027, 5, 2))).isFalse();
    }

    @Test
    void normalizaLasRespuestas() {
        Map<String, Object> limpias = ValidadorDeRespuestas.normalizar(paquete.preguntasOnboarding(),
                Map.of("ciudad", "  Lima ", "whatsapp", "987 654-321", "canales", List.of("WHATSAPP", "WHATSAPP"),
                        "distintivo", "   "));

        assertThat(limpias).containsEntry("ciudad", "Lima")
                .containsEntry("whatsapp", "+51987654321")
                .containsEntry("canales", List.of("WHATSAPP"))
                .doesNotContainKey("distintivo");
    }

    /** Pedido del usuario (2026-11-06): especialidad, clientes y canales admiten "Otros" con texto corto. */
    @Test
    void aceptaUnaOpcionEscritaEnOtros() {
        Map<String, Object> limpias = ValidadorDeRespuestas.normalizar(paquete.preguntasOnboarding(),
                Map.of("especialidad", List.of("TORTAS_PERSONALIZADAS", "  Cupcakes   veganos "),
                        "publico", List.of("Oficinas del centro")));

        assertThat(limpias).containsEntry("especialidad", List.of("TORTAS_PERSONALIZADAS", "Cupcakes veganos"))
                .containsEntry("publico", List.of("Oficinas del centro"));
        assertThatThrownBy(() -> ValidadorDeRespuestas.normalizar(paquete.preguntasOnboarding(),
                Map.of("ciudad", List.of("Lima")))).extracting("codigo").isEqualTo("RESPUESTA_INVALIDA");
    }

    @Test
    void rechazaRespuestasInvalidas() {
        var preguntas = paquete.preguntasOnboarding();
        assertThatThrownBy(() -> ValidadorDeRespuestas.normalizar(preguntas, Map.of("whatsapp", "12345")))
                .extracting("codigo").isEqualTo("RESPUESTA_INVALIDA");
        // "Otros" admite una sola opción escrita, de hasta 40 caracteres.
        assertThatThrownBy(() -> ValidadorDeRespuestas.normalizar(preguntas,
                Map.of("canales", List.of("Por Facebook", "Por teléfono")))).extracting("codigo")
                .isEqualTo("RESPUESTA_INVALIDA");
        assertThatThrownBy(() -> ValidadorDeRespuestas.normalizar(preguntas,
                Map.of("especialidad", List.of("x".repeat(41))))).extracting("codigo").isEqualTo("RESPUESTA_INVALIDA");
        assertThatThrownBy(() -> ValidadorDeRespuestas.normalizar(preguntas, Map.of("distintivo", "x".repeat(81))))
                .extracting("codigo").isEqualTo("RESPUESTA_INVALIDA");
        assertThatThrownBy(() -> ValidadorDeRespuestas.normalizar(preguntas, Map.of("inventada", "hola")))
                .extracting("codigo").isEqualTo("RESPUESTA_INVALIDA");
    }

    @Test
    void exigeLasObligatoriasYWhatsappSoloSiSeEligio() {
        var preguntas = paquete.preguntasOnboarding();
        Map<String, Object> respuestas = new HashMap<>(Map.of(
                "especialidad", List.of("TORTAS_PERSONALIZADAS"), "publico", List.of("FAMILIAS"),
                "ciudad", "Lima", "distrito", "Miraflores", "canales", List.of("TIENDA"),
                "horario", "Todos los días de 9 a 6"));

        assertThatCode(() -> ValidadorDeRespuestas.exigirCompletas(preguntas, respuestas)).doesNotThrowAnyException();

        respuestas.put("canales", List.of("TIENDA", "WHATSAPP"));
        assertThatThrownBy(() -> ValidadorDeRespuestas.exigirCompletas(preguntas, respuestas))
                .extracting("codigo").isEqualTo("PERFIL_INCOMPLETO");

        respuestas.put("whatsapp", "+51987654321");
        respuestas.remove("horario");
        assertThatThrownBy(() -> ValidadorDeRespuestas.exigirCompletas(preguntas, respuestas))
                .hasMessageContaining("Horario");
    }
}
