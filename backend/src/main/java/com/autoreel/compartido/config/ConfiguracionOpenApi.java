package com.autoreel.compartido.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConfiguracionOpenApi {

    @Bean
    OpenAPI documentacionApi() {
        return new OpenAPI().info(new Info()
                .title("AutoReel API")
                .version("fase-1")
                .description("API REST de AutoReel. La sesión viaja en la cookie HttpOnly autoreel_sesion; "
                        + "las operaciones que modifican datos requieren la cabecera X-XSRF-TOKEN."));
    }
}
