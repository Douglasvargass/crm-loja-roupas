package br.senai.crmloja.modules.crm.forecast.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Contratos da spec crm/12 (previsão de vendas e metas sugeridas). Espelho em crm-loja-frontend/src/data/dto.ts.
public final class ForecastDtos {

    private ForecastDtos() {}

    public record SalesForecast(LocalDate today, LocalDate historyFrom, int historyDays, Model model,
                                List<Period> periods, List<DayPoint> days) {}

    // Por que a previsão deu esse número: cada fator e de onde ele veio.
    public record Model(BigDecimal baselineRevenue, BigDecimal baselineSales, List<WeekdayFactor> weekday,
                        Factor payday, Factor holiday, MonthSeasonality monthSeasonality, List<EventInfo> events,
                        Accuracy accuracy) {}

    public record WeekdayFactor(String day, double factor) {}

    // learned = aprendido com o histórico; false = valor padrão (salário sem dados = 1; feriado = domingo).
    public record Factor(double factor, boolean learned) {}

    public record MonthSeasonality(boolean active, List<Double> factors) {}

    // Próxima ocorrência da data comercial. learned = false → estimativa padrão do varejo.
    public record EventInfo(String name, LocalDate date, int rampDays, double uplift, boolean learned) {}

    public record Accuracy(Double weeklyErrorPercent, int weeks) {}

    // realized: só no fechamento do mês atual (o que já vendeu até ontem; o resto é previsão).
    public record Period(String key, String label, LocalDate from, LocalDate to, BigDecimal revenue, BigDecimal low,
                         BigDecimal high, long sales, BigDecimal realized) {}

    // actual: o realizado (dias passados); revenue/low/high/sales: a previsão (hoje em diante).
    public record DayPoint(LocalDate date, BigDecimal actual, BigDecimal revenue, BigDecimal low, BigDecimal high,
                           Long sales, boolean holiday, List<String> events) {}

    // Metas sugeridas pela previsão (REQ-03).
    public record TargetSuggestions(String month, BigDecimal monthForecast, int growthPercent, LocalDate basisFrom,
                                    LocalDate basisTo, BigDecimal storeRevenue, List<SuggestionRow> rows) {}

    // suggested = null quando o vendedor não vendeu nos últimos 90 dias (reason explica).
    public record SuggestionRow(Long sellerId, String sellerName, BigDecimal revenue, BigDecimal sharePercent,
                                BigDecimal currentTarget, BigDecimal suggested, String reason) {}
}
