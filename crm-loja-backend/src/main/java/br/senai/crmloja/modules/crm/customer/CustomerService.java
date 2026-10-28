package br.senai.crmloja.modules.crm.customer;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.core.text.TextSearch;
import br.senai.crmloja.modules.crm.customer.dto.CustomerDetailResponse;
import br.senai.crmloja.modules.crm.customer.dto.CustomerRequest;
import br.senai.crmloja.modules.crm.customer.dto.CustomerResponse;
import br.senai.crmloja.modules.crm.sale.SaleRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA",
            "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, SaleRepository sales, AuditService audit) {
        this.customers = customers;
        this.sales = sales;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String search, Boolean active, int page, int size) {
        Specification<Customer> spec = (root, q, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (!TextSearch.isBlank(search)) {
                String digits = TextSearch.digits(search);
                Predicate byName = TextSearch.containsFolded(cb, root.get("fullName"), search);
                // "529.982" ou "52998" buscam pelo CPF; texto com letras busca só pelo nome.
                where.add(digits.length() >= 3 && !search.matches(".*[A-Za-zÀ-ÿ].*")
                        ? cb.or(byName, cb.like(root.get("cpf"), "%" + digits + "%"))
                        : byName);
            }
            if (active != null) where.add(cb.equal(root.get("active"), active));
            return cb.and(where.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), clamp(size), Sort.by("fullName").ascending());
        return PageResponse.of(customers.findAll(spec, pageable), CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse detail(Long id) {
        Customer c = find(id);
        var summary = sales.purchaseSummary(id);
        return new CustomerDetailResponse(CustomerResponse.from(c), summary.count(), summary.total(),
                summary.lastPurchaseAt());
    }

    @Transactional
    public CustomerResponse create(CustomerRequest req, Long actorId) {
        Customer c = new Customer(actorId);
        apply(c, req);
        return CustomerResponse.from(customers.save(c));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest req, Long actorId) {
        Customer c = find(id);
        apply(c, req);
        c.setUpdatedBy(actorId);
        return CustomerResponse.from(customers.saveAndFlush(c));
    }

    // RN02: cliente com vendas não some; a tela oferece inativar no lugar.
    @Transactional
    public void delete(Long id) {
        Customer c = find(id);
        long count = sales.countByCustomer_Id(id);
        if (count > 0) {
            throw new BusinessRuleException("customer_has_sales",
                    "Cliente possui " + count + " venda(s) e não pode ser excluído. Deseja inativá-lo?");
        }
        customers.delete(c);
    }

    @Transactional
    public CustomerResponse setActive(Long id, boolean active, Long actorId) {
        Customer c = find(id);
        if (c.isActive() != active) {
            c.setActive(active);
            c.setUpdatedBy(actorId);
            audit.record(actorId, "crm", active ? "customer_activated" : "customer_deactivated",
                    "customer", id, Map.of());
        }
        return CustomerResponse.from(c);
    }

    public Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Cliente não encontrado."));
    }

    private void apply(Customer c, CustomerRequest req) {
        String cpf = TextSearch.digits(req.cpf());
        if (!Cpf.isValid(cpf)) throw new InvalidInputException("invalid_cpf", "CPF inválido.");
        customers.findByCpf(cpf)
                .filter(other -> !other.getId().equals(c.getId()))
                .ifPresent(other -> {
                    throw new BusinessRuleException("cpf_taken", "Já existe cliente com este CPF.");
                });

        String phone = TextSearch.digits(req.phone());
        if (!phone.matches("\\d{10,11}")) {
            throw new InvalidInputException("invalid_phone", "Telefone inválido: use DDD + número.");
        }
        String zip = TextSearch.digits(req.zipCode());
        if (!zip.matches("\\d{8}")) throw new InvalidInputException("invalid_zip", "CEP inválido.");
        String uf = req.state().trim().toUpperCase();
        if (!UFS.contains(uf)) throw new InvalidInputException("invalid_state", "UF inválida.");

        c.setFullName(req.fullName().trim().replaceAll("\\s+", " "));
        c.setCpf(cpf);
        c.setPhone(phone);
        c.setEmail(req.email().trim().toLowerCase());
        c.setZipCode(zip);
        c.setStreet(req.street().trim());
        c.setNumber(req.number().trim());
        c.setComplement(TextSearch.isBlank(req.complement()) ? null : req.complement().trim());
        c.setDistrict(req.district().trim());
        c.setCity(req.city().trim());
        c.setState(uf);
    }

    static int clamp(int size) { return Math.min(Math.max(size, 1), 100); }
}
