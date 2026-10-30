package br.senai.crmloja.modules.crm.product.image;

import br.senai.crmloja.core.audit.AuditService;
import br.senai.crmloja.core.http.BusinessRuleException;
import br.senai.crmloja.core.http.InvalidInputException;
import br.senai.crmloja.core.http.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Galeria de imagens dos produtos (catálogo visual). A imagem fica no banco (V13). O tipo é conferido pelos
// primeiros bytes do arquivo, não pelo nome nem pelo que o navegador declarou.
@Service
public class ProductImageService {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_PER_PRODUCT = 8;

    public enum View { FRONT, BACK, SIDE, DETAIL, OTHER }

    public record ImageInfo(Long id, View view, int position, String url, String source, String license,
                            boolean illustration) {}

    public record ImageData(String contentType, byte[] data) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public ProductImageService(NamedParameterJdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ImageInfo> list(Long productId) {
        requireProduct(productId);
        return jdbc.query("""
                select id, view, position, source, license, illustration
                  from product_image where product_id = :p order by position""",
                Map.of("p", productId), (rs, i) -> new ImageInfo(rs.getLong("id"), View.valueOf(rs.getString("view")),
                        rs.getInt("position"), "/media/images/" + rs.getLong("id"), rs.getString("source"),
                        rs.getString("license"), rs.getBoolean("illustration")));
    }

    @Transactional(readOnly = true)
    public Optional<ImageData> image(Long imageId) {
        return one("select content_type, data from product_image where id = :id", Map.of("id", imageId));
    }

    // Capa = a primeira imagem da galeria.
    @Transactional(readOnly = true)
    public Optional<ImageData> cover(Long productId) {
        return one("select content_type, data from product_image where product_id = :p order by position limit 1",
                Map.of("p", productId));
    }

    @Transactional
    public ImageInfo upload(Long productId, byte[] data, View view, Long actorId, String actorName) {
        requireProduct(productId);
        if (data == null || data.length == 0) {
            throw new InvalidInputException("empty_image", "Escolha uma imagem.");
        }
        if (data.length > MAX_BYTES) {
            throw new InvalidInputException("image_too_large", "A imagem pode ter no máximo 2 MB.");
        }
        String type = sniff(data);
        if (type == null) {
            throw new InvalidInputException("invalid_image", "Use uma imagem JPG, PNG ou WEBP.");
        }
        Integer count = jdbc.queryForObject("select count(*) from product_image where product_id = :p",
                Map.of("p", productId), Integer.class);
        if (count != null && count >= MAX_PER_PRODUCT) {
            throw new BusinessRuleException("too_many_images", "Cada produto pode ter até " + MAX_PER_PRODUCT + " imagens.");
        }
        return insert(productId, view == null ? View.OTHER : view, type, data,
                "Enviada por " + (actorName == null ? "alguém da equipe" : actorName), "Própria da loja", false, actorId);
    }

    // Usado pelo seed das ilustrações de demonstração e pelo upload.
    public ImageInfo insert(Long productId, View view, String type, byte[] data, String source, String license,
                     boolean illustration, Long actorId) {
        Integer next = jdbc.queryForObject("select coalesce(max(position) + 1, 0) from product_image where product_id = :p",
                Map.of("p", productId), Integer.class);
        var params = new MapSqlParameterSource()
                .addValue("p", productId).addValue("v", view.name()).addValue("pos", next)
                .addValue("t", type).addValue("d", data).addValue("n", data.length)
                .addValue("s", source).addValue("l", license).addValue("il", illustration).addValue("u", actorId);
        Long id = jdbc.queryForObject("""
                insert into product_image (product_id, view, position, content_type, data, size_bytes, source, license,
                                           illustration, created_by)
                values (:p, :v, :pos, :t, :d, :n, :s, :l, :il, :u) returning id""", params, Long.class);
        if (actorId != null) {
            audit.record(actorId, "crm", "product_image_added", "product", productId, Map.of("imageId", id, "view", view.name()));
        }
        return new ImageInfo(id, view, next == null ? 0 : next, "/media/images/" + id, source, license, illustration);
    }

    @Transactional
    public void delete(Long productId, Long imageId, Long actorId) {
        int n = jdbc.update("delete from product_image where id = :id and product_id = :p", Map.of("id", imageId, "p", productId));
        if (n == 0) throw new NotFoundException("Imagem não encontrada.");
        renumber(productId);
        audit.record(actorId, "crm", "product_image_removed", "product", productId, Map.of("imageId", imageId));
    }

    // Leva a imagem para a primeira posição (vira a capa) e mantém a ordem das outras.
    @Transactional
    public void makeCover(Long productId, Long imageId, Long actorId) {
        int n = jdbc.update("update product_image set position = -1 where id = :id and product_id = :p",
                Map.of("id", imageId, "p", productId));
        if (n == 0) throw new NotFoundException("Imagem não encontrada.");
        renumber(productId);
        audit.record(actorId, "crm", "product_image_cover", "product", productId, Map.of("imageId", imageId));
    }

    @Transactional(readOnly = true)
    public boolean hasImages(Long productId) {
        Integer c = jdbc.queryForObject("select count(*) from product_image where product_id = :p", Map.of("p", productId), Integer.class);
        return c != null && c > 0;
    }

    private void renumber(Long productId) {
        jdbc.update("""
                update product_image i set position = r.rn - 1
                  from (select id, row_number() over (order by position, id) as rn from product_image where product_id = :p) r
                 where i.id = r.id""", Map.of("p", productId));
    }

    private void requireProduct(Long productId) {
        Boolean exists = jdbc.queryForObject("select exists(select 1 from product where id = :p)", Map.of("p", productId), Boolean.class);
        if (!Boolean.TRUE.equals(exists)) throw new NotFoundException("Produto não encontrado.");
    }

    private Optional<ImageData> one(String sql, Map<String, ?> params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params,
                    (rs, i) -> new ImageData(rs.getString("content_type"), rs.getBytes("data"))));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // Tipo real do arquivo pelos primeiros bytes ("assinatura"). null = não é imagem aceita.
    static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
