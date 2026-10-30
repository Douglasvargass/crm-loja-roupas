package br.senai.crmloja.modules.crm.product.image;

import br.senai.crmloja.core.auth.CurrentUser;
import br.senai.crmloja.modules.crm.product.image.ProductImageService.ImageData;
import br.senai.crmloja.modules.crm.product.image.ProductImageService.ImageInfo;
import br.senai.crmloja.modules.crm.product.image.ProductImageService.View;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Galeria dos produtos. As imagens em si (/media/**) são públicas, como numa vitrine: a loja online e a tela de
// venda mostram a foto sem precisar de login. Quem muda a galeria é o gerente.
@RestController
public class ProductImageController {

    private final ProductImageService service;

    public ProductImageController(ProductImageService service) { this.service = service; }

    @GetMapping("/crm/products/{id}/images")
    public List<ImageInfo> list(@PathVariable Long id) {
        return service.list(id);
    }

    @PostMapping(value = "/crm/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public ImageInfo upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                            @RequestParam(required = false) View view, @AuthenticationPrincipal CurrentUser me) throws IOException {
        return service.upload(id, file.getBytes(), view, me.id(), me.fullName());
    }

    @DeleteMapping("/crm/products/{id}/images/{imageId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable Long imageId, @AuthenticationPrincipal CurrentUser me) {
        service.delete(id, imageId, me.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/crm/products/{id}/images/{imageId}/cover")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> cover(@PathVariable Long id, @PathVariable Long imageId, @AuthenticationPrincipal CurrentUser me) {
        service.makeCover(id, imageId, me.id());
        return ResponseEntity.noContent().build();
    }

    // A imagem não muda depois de gravada (cada envio ganha um id novo): pode ficar um dia no cache do navegador.
    @GetMapping("/media/images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable Long imageId) {
        return service.image(imageId).map(d -> send(d, CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic()))
                .orElse(ResponseEntity.notFound().build());
    }

    // A capa pode mudar: cache curto.
    @GetMapping("/media/products/{productId}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable Long productId) {
        return service.cover(productId).map(d -> send(d, CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic()))
                .orElse(ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> send(ImageData d, CacheControl cache) {
        return ResponseEntity.ok().cacheControl(cache).contentType(MediaType.parseMediaType(d.contentType()))
                .header("X-Content-Type-Options", "nosniff").body(d.data());
    }
}
