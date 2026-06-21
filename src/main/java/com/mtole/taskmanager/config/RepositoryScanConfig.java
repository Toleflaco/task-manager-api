package com.mtole.taskmanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
        "com.mtole.taskmanager.auth",
        "com.mtole.taskmanager.categories",
        "com.mtole.taskmanager.tasks",
        "com.mtole.taskmanager.users"
})
@EnableMongoRepositories(basePackages = "com.mtole.taskmanager.activity")
public class RepositoryScanConfig {
}
