package br.senai.crmloja.modules.crm.seed;

import br.senai.crmloja.modules.crm.product.image.ProductImageService;
import br.senai.crmloja.modules.crm.product.image.ProductImageService.View;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Banco de imagens de demonstração: 4 vistas (frente, costas, lateral, detalhe) de cada produto do catálogo inicial,
// em src/main/resources/assets/produtos/<pasta>/. São ILUSTRAÇÕES feitas pela equipe em código
// (ferramentas/imagens-produtos), não fotos: ficam marcadas como ilustração e com a licença.
// Idempotente: só grava em produto que ainda não tem nenhuma imagem; vale também no banco que já existe no Render.
@Component
@Profile({"dev", "demo"})
@Order(32)
public class ImagensProdutosSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImagensProdutosSeeder.class);

    static final String SOURCE = "Equipe Orden, desenho gerado por código (ferramentas/imagens-produtos)";
    static final String LICENSE = "CC0 · uso livre no projeto";

    // Código do produto no catálogo de demonstração → pasta das imagens.
    static final Map<String, String> PASTAS = new LinkedHashMap<>();
    static {
        PASTAS.put("ACE-002-U-PT", "bone_preto");
        PASTAS.put("CAL-001-40-AZ", "calca_jeans_azul");
        PASTAS.put("CAL-002-42-PT", "calca_sarja_preta");
        PASTAS.put("CAM-002-G-BR", "camiseta_branca_g");
        PASTAS.put("CAM-001-M-BR", "camiseta_branca_m");
        PASTAS.put("CAM-003-P-PT", "camiseta_estampada_preta");
        PASTAS.put("ACE-001-U-MR", "cinto_marrom");
        PASTAS.put("ACE-003-U-CZ", "kit_meias_cinza");
        PASTAS.put("VES-002-P-AZ", "vestido_linho_azul");
        PASTAS.put("VES-001-M-VM", "vestido_floral_vermelho");
        PASTAS.put("JAQ-001-M-PT", "jaqueta_couro_preta");
        PASTAS.put("JAQ-002-G-AZ", "jaqueta_jeans_azul");
    }

    static final List<Map.Entry<String, View>> VISTAS = List.of(
            Map.entry("frente", View.FRONT), Map.entry("traseira", View.BACK),
            Map.entry("lateral", View.SIDE), Map.entry("detalhes", View.DETAIL));

    private final JdbcTemplate jdbc;
    private final ProductImageService images;
    private final TransactionTemplate tx;

    public ImagensProdutosSeeder(JdbcTemplate jdbc, ProductImageService images, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.images = images;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        int added = 0;
        for (var e : PASTAS.entrySet()) {
            List<Long> ids = jdbc.queryForList("select id from product where code = ?", Long.class, e.getKey());
            if (ids.isEmpty() || images.hasImages(ids.get(0))) continue;
            Long productId = ids.get(0);
            try {
                tx.executeWithoutResult(s -> {
                    for (var v : VISTAS) {
                        images.insert(productId, v.getValue(), "image/jpeg", read(e.getValue(), v.getKey()), SOURCE, LICENSE, true, null);
                    }
                });
                added++;
            } catch (RuntimeException ex) {
                log.error("Imagens de {} não foram gravadas; a aplicação segue no ar", e.getKey(), ex);
            }
        }
        if (added > 0) log.info("Banco de imagens: {} produto(s) receberam as 4 ilustrações", added);
    }

    private static byte[] read(String folder, String view) {
        try (InputStream in = new ClassPathResource("assets/produtos/" + folder + "/" + view + ".jpg").getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Imagem não encontrada: " + folder + "/" + view, ex);
        }
    }
}
