package br.senai.crmloja.modules.crm.stock.dto;

import br.senai.crmloja.modules.crm.product.dto.ProductResponse;
import java.util.List;

public record StockAlertsResponse(List<ProductResponse> lowStock, List<ProductResponse> outOfStock) {
}
