package br.senai.crmloja.core.audit;

import br.senai.crmloja.core.rbac.Module;
import br.senai.crmloja.core.rbac.ModuleRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository events;
    private final ModuleRepository modules;

    public AuditService(AuditEventRepository events, ModuleRepository modules) {
        this.events = events;
        this.modules = modules;
    }

    // MANDATORY: o evento só existe se a operação auditada também for gravada (mesma transação).
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, String moduleCode, String action, String entityType, Long entityId,
                       Map<String, Object> payload) {
        Long moduleId = moduleCode == null ? null : modules.findByCode(moduleCode).map(Module::getId).orElse(null);
        events.save(new AuditEvent(actorId, moduleId, action, entityType, entityId, payload));
    }
}
