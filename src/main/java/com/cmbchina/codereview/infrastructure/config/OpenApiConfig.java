package com.cmbchina.codereview.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI codeReviewOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("AI Code Review Platform API")
                .version("0.0.1")
                .description("AI driven code review platform"));
    }
}
