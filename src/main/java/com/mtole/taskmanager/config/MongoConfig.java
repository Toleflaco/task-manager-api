package com.mtole.taskmanager.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración manual del cliente MongoDB.
 *
 * Construimos el MongoClient directamente desde el connection string
 * en lugar de delegar en la autoconfig de Spring Boot, por un problema
 * conocido en Spring Boot 4.0.6 + driver mongo 5.6.5 donde la URI
 * llega al contexto pero no se aplica al cliente (cae en defaults
 * localhost:27017).
 */
@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    public MongoClient mongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();
        return MongoClients.create(settings);
    }
}