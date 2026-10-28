package br.senai.crmloja;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.File;
import java.io.IOException;
import org.springframework.boot.SpringApplication;

// Modo "sem Docker" para estudar e apresentar: sobe um Postgres 16 embutido gravando em
// ./.data/postgres (os dados ficam entre execuções) e roda a aplicação no perfil dev.
//   ./mvnw spring-boot:test-run
// Para zerar o banco, apague a pasta .data. Em produção use o docker-compose/Postgres de verdade.
public class TestCrmApplication {

    public static void main(String[] args) throws IOException {
        File dataDir = new File(".data/postgres");
        boolean fresh = !new File(dataDir, "PG_VERSION").exists();
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                // Porta fora do padrão para não colidir com um Postgres já instalado na máquina.
                .setPort(Integer.parseInt(System.getenv().getOrDefault("EMBEDDED_PG_PORT", "54329")))
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { pg.close(); } catch (IOException ignored) { }
        }));
        if (fresh) System.out.println("Banco novo criado em " + dataDir.getAbsolutePath());

        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        SpringApplication app = new SpringApplication(CrmApplication.class);
        app.setAdditionalProfiles("dev");
        app.run(args);
    }
}
