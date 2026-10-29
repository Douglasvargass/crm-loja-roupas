package br.senai.crmloja.modules.crm.seed;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

// Liga o relógio (@Scheduled) só na demonstração: em uso real e nos testes, nada roda sozinho.
@Configuration
@Profile({"dev", "demo"})
@EnableScheduling
class AgendamentoDemoConfig {
}
