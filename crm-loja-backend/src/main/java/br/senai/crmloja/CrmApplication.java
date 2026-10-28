package br.senai.crmloja;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Composition root: único ponto que conhece núcleo (core) e módulo (modules/crm).
@SpringBootApplication
@ConfigurationPropertiesScan
public class CrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
