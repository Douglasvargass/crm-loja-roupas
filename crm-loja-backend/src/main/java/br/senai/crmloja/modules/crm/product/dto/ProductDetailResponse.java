package br.senai.crmloja.modules.crm.product.dto;

import br.senai.crmloja.modules.crm.stock.dto.StockMovementResponse;
import java.util.List;

// Ficha do produto (HU11): dados, últimas movimentações e total vendido em vendas confirmadas.
public record ProductDetailResponse(ProductResponse product, List<StockMovementResponse> recentMovements,
                                    long totalSold) {
}
