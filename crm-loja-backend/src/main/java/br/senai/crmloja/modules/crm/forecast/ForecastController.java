package br.senai.crmloja.modules.crm.forecast;

import br.senai.crmloja.modules.crm.forecast.dto.ForecastDtos.SalesForecast;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Spec crm/12. Previsão de vendas: só gerente (e a equipe técnica, que vale por gerente).
@RestController
@RequestMapping("/crm")
public class ForecastController {

    private final ForecastService service;

    public ForecastController(ForecastService service) { this.service = service; }

    @GetMapping("/forecast")
    @PreAuthorize("hasRole('MANAGER')")
    public SalesForecast forecast(@RequestParam(required = false) Integer months) {
        return service.forecast(months);
    }
}
