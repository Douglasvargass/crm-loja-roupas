package br.senai.crmloja.modules.crm.target;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.SellerPanel;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.TargetBoard;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.TargetRequest;
import br.senai.crmloja.modules.crm.target.dto.TargetDtos.TargetSaved;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Spec crm/06. Metas: só gerente (e a equipe técnica, que vale por gerente). Meu painel: o vendedor, sempre com o
// próprio id da sessão; não existe parâmetro para pedir o painel de outra pessoa.
@RestController
@RequestMapping("/crm")
public class TargetController {

    private final TargetService service;

    public TargetController(TargetService service) { this.service = service; }

    @GetMapping("/targets")
    @PreAuthorize("hasRole('MANAGER')")
    public TargetBoard board(@RequestParam(required = false) String month) {
        return service.board(service.parseMonth(month));
    }

    @PutMapping("/targets/{sellerId}")
    @PreAuthorize("hasRole('MANAGER')")
    public TargetSaved set(@PathVariable Long sellerId, @RequestParam(required = false) String month,
                           @Valid @RequestBody TargetRequest body, @AuthenticationPrincipal CurrentUser actor) {
        return service.set(sellerId, service.parseMonth(month), body.amount(), actor.id());
    }

    @GetMapping("/my-panel")
    @PreAuthorize("hasRole('SELLER')")
    public SellerPanel myPanel(@RequestParam(required = false) String month, @AuthenticationPrincipal CurrentUser me) {
        return service.panel(me.id(), service.parseMonth(month));
    }
}
