package com.example.podcastbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI podcastOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Podcast Backend API")
                        .description("API for searching podcasts, episodes, and retrieving rankings")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Podcast Team")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")
                ));
    }
}
