package br.senai.crmloja.modules.crm.demo;

import br.senai.crmloja.modules.crm.assistant.AssistantService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Diário do agente de vendas da demonstração (spec crm/08), para o gerente ver o que o agente fez e por quê.
// Fora dos perfis dev/demo o agente não existe: a rota responde enabled=false e a lista vazia.
@RestController
@RequestMapping("/crm/demo-agent")
@PreAuthorize("hasRole('MANAGER')")
public class DemoAgentController {

    public record Day(LocalDate day, String status, String source, BigDecimal demandFactor, String highlightFamily,
                      BigDecimal highlightFactor, String reason, int salesCreated, BigDecimal revenue,
                      OffsetDateTime createdAt) {}

    public record Diary(boolean enabled, boolean aiEnabled, List<Day> days) {}

    private final JdbcTemplate jdbc;
    private final Environment env;
    private final AssistantService ai;

    public DemoAgentController(JdbcTemplate jdbc, Environment env, AssistantService ai) {
        this.jdbc = jdbc;
        this.env = env;
        this.ai = ai;
    }

    @GetMapping
    public Diary diary() {
        boolean enabled = env.acceptsProfiles(Profiles.of("dev", "demo"));
        List<Day> days = jdbc.query("""
                select day, status, source, demand_factor, highlight_family, highlight_factor, reason,
                       sales_created, revenue, created_at
                  from demo_agent_day order by day desc limit 14""",
                (rs, i) -> new Day(rs.getObject("day", LocalDate.class), rs.getString("status"), rs.getString("source"),
                        rs.getBigDecimal("demand_factor"), rs.getString("highlight_family"),
                        rs.getBigDecimal("highlight_factor"), rs.getString("reason"), rs.getInt("sales_created"),
                        rs.getBigDecimal("revenue"), rs.getObject("created_at", OffsetDateTime.class)));
        return new Diary(enabled, ai.enabled(), days);
    }
}
