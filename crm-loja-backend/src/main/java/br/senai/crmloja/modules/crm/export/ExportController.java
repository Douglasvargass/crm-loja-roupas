package br.senai.crmloja.modules.crm.export;

import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.customer.CustomerService;
import br.senai.crmloja.modules.crm.customer.dto.CustomerResponse;
import br.senai.crmloja.modules.crm.product.ProductService;
import br.senai.crmloja.modules.crm.product.ProductService.AvailabilityFilter;
import br.senai.crmloja.modules.crm.product.dto.ProductResponse;
import br.senai.crmloja.modules.crm.sale.SaleService;
import br.senai.crmloja.modules.crm.sale.SaleStatus;
import br.senai.crmloja.modules.crm.sale.dto.SaleSummary;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.IntFunction;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// HU32 (o único "Could" que tinha ficado de fora): exportar clientes, produtos e vendas para planilha. Só o gerente.
// Usa os mesmos filtros e a mesma busca das telas (os Services), página a página, então o arquivo bate com a lista.
// Formato pensado para o Excel em português: separador ";", vírgula decimal e BOM UTF-8 (acentos certos ao abrir).
@RestController
@RequestMapping("/crm/export")
@PreAuthorize("hasRole('MANAGER')")
public class ExportController {

    private static final int PAGE = 100;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CustomerService customers;
    private final ProductService products;
    private final SaleService sales;

    public ExportController(CustomerService customers, ProductService products, SaleService sales) {
        this.customers = customers;
        this.products = products;
        this.sales = sales;
    }

    @GetMapping("/customers.csv")
    public void customers(@RequestParam(required = false) String search, @RequestParam(required = false) Boolean active,
                          HttpServletResponse res) throws IOException {
        PrintWriter out = open(res, "clientes");
        line(out, "Id", "Nome", "CPF", "Telefone", "E-mail", "CEP", "Rua", "Número", "Complemento", "Bairro", "Cidade",
                "UF", "Situação", "Cadastrado em");
        each(p -> customers.search(search, active, p, PAGE), (CustomerResponse c) -> line(out,
                c.id(), c.fullName(), c.cpf(), c.phone(), c.email(), c.zipCode(), c.street(), c.number(), c.complement(),
                c.district(), c.city(), c.state(), c.active() ? "Ativo" : "Inativo", data(c.createdAt())));
        out.flush();
    }

    @GetMapping("/products.csv")
    public void products(@RequestParam(required = false) String name, @RequestParam(required = false) Long categoryId,
                         @RequestParam(required = false) String size, @RequestParam(required = false) String code,
                         @RequestParam(required = false) AvailabilityFilter availability,
                         HttpServletResponse res) throws IOException {
        PrintWriter out = open(res, "produtos");
        line(out, "Id", "Código", "Nome", "Categoria", "Tamanho", "Cor", "Preço", "Estoque", "Estoque mínimo", "Situação");
        each(p -> products.search(name, categoryId, size, code, availability, p, PAGE), (ProductResponse x) -> line(out,
                x.id(), x.code(), x.name(), x.categoryName(), x.size(), x.color(), x.price(), x.stockQty(), x.minStock(),
                !x.active() ? "Inativo" : x.stockQty() == 0 ? "Sem estoque" : x.lowStock() ? "Estoque baixo" : "Disponível"));
        out.flush();
    }

    @GetMapping("/sales.csv")
    public void sales(@RequestParam(required = false) Long customerId,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(required = false) SaleStatus status,
                      HttpServletResponse res) throws IOException {
        PrintWriter out = open(res, "vendas");
        line(out, "Número", "Aberta em", "Confirmada em", "Cliente", "Itens", "Total", "Situação");
        each(p -> sales.search(null, customerId, from, to, status, p, PAGE), (SaleSummary s) -> line(out,
                s.id(), data(s.createdAt()), data(s.confirmedAt()),
                s.customerName() == null ? "Consumidor não identificado" : s.customerName(),
                s.itemsQuantity(), s.total(), switch (s.status()) {
                    case DRAFT -> "Aberta";
                    case CONFIRMED -> "Confirmada";
                    case CANCELLED -> "Cancelada";
                }));
        out.flush();
    }

    // ------------------------------------------------------------------------------------------------ CSV

    private static <T> void each(IntFunction<PageResponse<T>> page, java.util.function.Consumer<T> row) {
        for (int p = 0; ; p++) {
            PageResponse<T> r = page.apply(p);
            r.content().forEach(row);
            if (p + 1 >= r.totalPages()) return;
        }
    }

    private static PrintWriter open(HttpServletResponse res, String name) throws IOException {
        res.setContentType("text/csv; charset=UTF-8");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setHeader("Content-Disposition", "attachment; filename=\"orden-" + name + "-" + LocalDate.now() + ".csv\"");
        PrintWriter out = res.getWriter();
        out.write('﻿');   // BOM: o Excel abre em UTF-8
        return out;
    }

    static void line(PrintWriter out, Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(cell(values[i]));
        }
        out.write(sb.append("\r\n").toString());
    }

    // Aspas quando precisa; vírgula decimal; e um apóstrofo antes de =, +, - ou @, para o Excel não executar
    // fórmula vinda de um campo digitado (injeção de fórmula em CSV).
    static String cell(Object v) {
        if (v == null) return "";
        String s = v instanceof BigDecimal b ? b.toPlainString().replace('.', ',') : v.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) s = "'" + s;
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String data(OffsetDateTime t) {
        return t == null ? "" : t.atZoneSameInstant(java.time.ZoneId.of("America/Sao_Paulo")).format(DATA_HORA);
    }
}
