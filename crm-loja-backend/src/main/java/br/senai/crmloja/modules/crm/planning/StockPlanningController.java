package br.senai.crmloja.modules.crm.planning;

import br.senai.crmloja.modules.crm.planning.StockPlanningService.Planning;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Spec crm/11: painel "O que vai faltar". Mesmos papéis da tela de estoque (gerente, estoquista, Operações e Compras).
@RestController
@PreAuthorize("hasAnyRole('MANAGER','STOCK_KEEPER')")
public class StockPlanningController {

    private final StockPlanningService service;

    public StockPlanningController(StockPlanningService service) { this.service = service; }

    // horizon = quantos dias o pedido sugerido deve cobrir (7 a 90; padrão 30).
    @GetMapping("/crm/stock/planning")
    public Planning planning(@RequestParam(required = false) Integer horizon) {
        return service.build(horizon);
    }
}
