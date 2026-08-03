package com.notare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerCustomConfig {

    @Bean
    public OpenAPI openAPI() {

        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Notare API")
                        .description("REST APIs for the Notare Application")
                        .version("1.0")
                        .contact(new Contact()
                                .name("NOTARE")
                                .email("ezemosesjohn21997@gmail.com")
                                .url("https://www.notare.com"))
                        .license(new License()
                                .name("API License")
                                .url("https://www.notare.com")))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", bearerAuth))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("Bearer Authentication")
                );
    }
}