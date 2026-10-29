package br.senai.crmloja.modules.crm.stock.dto;

import br.senai.crmloja.modules.crm.product.dto.ProductResponse;
import java.util.List;

// As duas listas separadas porque a tela trata diferente: zerado não vende, baixo ainda vende mas precisa repor.
public record StockAlertsResponse(List<ProductResponse> lowStock, List<ProductResponse> outOfStock) {
}
