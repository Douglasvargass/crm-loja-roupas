package br.senai.crmloja.modules.crm.store;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.core.http.PageResponse;
import br.senai.crmloja.modules.crm.customer.dto.CustomerResponse;
import br.senai.crmloja.modules.crm.store.StoreService.Cart;
import br.senai.crmloja.modules.crm.store.StoreService.Category;
import br.senai.crmloja.modules.crm.store.StoreService.MyData;
import br.senai.crmloja.modules.crm.store.StoreService.Order;
import br.senai.crmloja.modules.crm.store.StoreService.OrderDetail;
import br.senai.crmloja.modules.crm.store.StoreService.ShopProduct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import br.senai.crmloja.modules.crm.request.StockRequest;
import br.senai.crmloja.modules.crm.request.StockRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Espaço do cliente (spec crm/07). Só o papel CUSTOMER (módulo loja); a equipe usa as telas de venda.
// Nenhuma rota recebe id de cliente: tudo vem da sessão (StoreService.me).
@RestController
@RequestMapping("/loja")
@PreAuthorize("hasRole('CUSTOMER')")
public class StoreController {

    public record AddItem(@NotNull(message = "Escolha o produto.") Long productId,
                          @NotNull(message = "Informe a quantidade.") @Min(value = 1, message = "A quantidade deve ser de 1 a 99.")
                          @Max(value = 99, message = "A quantidade deve ser de 1 a 99.") Integer quantity) {}

    public record ChangeItem(@NotNull(message = "Informe a quantidade.") @Min(value = 0, message = "Quantidade inválida.")
                             @Max(value = 99, message = "A quantidade deve ser de 1 a 99.") Integer quantity) {}

    public record Checkout(@NotBlank(message = "Escolha Pix ou cartão.") String method, String cardNumber) {}

    public record MyDataRequest(
            @NotBlank(message = "Informe o telefone.") String phone,
            @NotBlank(message = "Informe o e-mail.") @Size(max = 120) String email,
            @NotBlank(message = "Informe o CEP.") String zipCode,
            @NotBlank(message = "Informe a rua.") @Size(max = 120) String street,
            @NotBlank(message = "Informe o número.") @Size(max = 10) String number,
            @Size(max = 60) String complement,
            @NotBlank(message = "Informe o bairro.") @Size(max = 60) String district,
            @NotBlank(message = "Informe a cidade.") @Size(max = 60) String city,
            @NotBlank(message = "Informe a UF.") String state) {}

    private final StoreService store;
    private final StockRequestService requests;

    public StoreController(StoreService store, StockRequestService requests) {
        this.store = store;
        this.requests = requests;
    }

    // Solicitação de peça que a loja não tem (pedido do Douglas, 29/10/2026): o cliente pede pela vitrine ou pela
    // Talita; vira um chamado de peça em falta (spec crm/09) com o cliente já ligado, e o gerente e o estoque veem no
    // sino. O cliente acompanha as dele em Meus pedidos. Peça com estoque o sistema recusa: é para comprar na vitrine.
    public record PartRequest(
            Long productId,
            @NotBlank(message = "Descreva a peça que você procura.") @Size(max = 200, message = "Descrição muito longa.") String description,
            @Size(max = 10, message = "Tamanho muito longo.") String size,
            @Size(max = 30, message = "Cor muito longa.") String color,
            @Min(value = 1, message = "A quantidade deve ser de 1 a 99.") @Max(value = 99, message = "A quantidade deve ser de 1 a 99.") Integer quantity) {}

    @GetMapping("/requests")
    public PageResponse<StockRequestService.Row> myRequests(@AuthenticationPrincipal CurrentUser me) {
        return requests.search(null, me.id(), "CUSTOMER", 0, 50);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public StockRequestService.Row requestPart(@Valid @RequestBody PartRequest body, @AuthenticationPrincipal CurrentUser me) {
        return requests.open(new StockRequestService.Input(body.productId(), body.description(), body.size(), body.color(),
                body.quantity(), store.customerIdOf(me.id())), StockRequest.Via.SCREEN, me.id());
    }

    // Vitrine e categorias também para o gerente (e a equipe técnica, que vale por gerente): é o que a "Prévia da loja"
    // mostra. Só leitura e sem dado de cliente; carrinho, pedidos e dados continuam só do cliente.
    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER')")
    public PageResponse<ShopProduct> products(@RequestParam(required = false) String name,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) String size,
                                              @RequestParam(defaultValue = "0") int page) {
        return store.catalog(name, categoryId, size, page);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER')")
    public List<Category> categories() { return store.categories(); }

    @GetMapping("/cart")
    public Cart cart(@AuthenticationPrincipal CurrentUser me) { return store.cart(me.id()); }

    @PostMapping("/cart/items")
    public Cart add(@Valid @RequestBody AddItem body, @AuthenticationPrincipal CurrentUser me) {
        return store.add(me.id(), body.productId(), body.quantity());
    }

    @PutMapping("/cart/items/{itemId}")
    public Cart change(@PathVariable Long itemId, @Valid @RequestBody ChangeItem body, @AuthenticationPrincipal CurrentUser me) {
        return store.update(me.id(), itemId, body.quantity());
    }

    @DeleteMapping("/cart/items/{itemId}")
    public Cart remove(@PathVariable Long itemId, @AuthenticationPrincipal CurrentUser me) {
        return store.remove(me.id(), itemId);
    }

    @PostMapping("/checkout")
    public OrderDetail checkout(@Valid @RequestBody Checkout body, @AuthenticationPrincipal CurrentUser me) {
        return store.checkout(me.id(), body.method(), body.cardNumber());
    }

    @GetMapping("/orders")
    public List<Order> orders(@AuthenticationPrincipal CurrentUser me) { return store.orders(me.id()); }

    @GetMapping("/orders/{id}")
    public OrderDetail order(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) { return store.order(me.id(), id); }

    @GetMapping("/me")
    public CustomerResponse myData(@AuthenticationPrincipal CurrentUser me) { return store.myData(me.id()); }

    @PutMapping("/me")
    public CustomerResponse updateMyData(@Valid @RequestBody MyDataRequest b, @AuthenticationPrincipal CurrentUser me) {
        return store.updateMyData(me.id(), new MyData(b.phone(), b.email(), b.zipCode(), b.street(), b.number(),
                b.complement(), b.district(), b.city(), b.state()));
    }
}
