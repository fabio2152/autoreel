package com.autoreel.compartido.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.autoreel.PruebaDeIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** El backend sirve el frontend compilado (fase 8): rutas de React Router, archivos con hash y la API intacta. */
class FrontendIntegracionTest extends PruebaDeIntegracion {

    static final Path FRONTEND = frontendCompilado();

    @DynamicPropertySource
    static void ubicacion(DynamicPropertyRegistry registro) {
        registro.add("spring.web.resources.static-locations", () -> FRONTEND.toUri().toString());
    }

    @Test
    void lasRutasDeReactEntreganIndexSinCache() throws Exception {
        mvc.perform(get("/videos/5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("app-autoreel")))
                .andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/admin/negocios/3")).andExpect(status().isOk())
                .andExpect(content().string(containsString("app-autoreel")));
    }

    @Test
    void losArchivosConHashVanEnCacheYLosQueFaltanDan404() throws Exception {
        mvc.perform(get("/assets/index-abc123.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")));
        mvc.perform(get("/assets/no-existe.js")).andExpect(status().isNotFound());
    }

    @Test
    void laApiYElEstadoNoRecibenIndex() throws Exception {
        mvc.perform(get("/api/no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/api/admin/no-existe").cookie(sesionAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private static Path frontendCompilado() {
        try {
            Path carpeta = Files.createTempDirectory("autoreel-frontend-");
            Files.createDirectories(carpeta.resolve("assets"));
            Files.writeString(carpeta.resolve("index.html"), "<!doctype html><div id=\"app-autoreel\"></div>");
            Files.writeString(carpeta.resolve("assets/index-abc123.js"), "console.log('autoreel')");
            return carpeta;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
