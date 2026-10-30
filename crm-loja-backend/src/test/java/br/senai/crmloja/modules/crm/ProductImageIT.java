package br.senai.crmloja.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senai.crmloja.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

// Galeria de imagens dos produtos (V13): enviar, listar, capa pública, tornar capa, excluir e as recusas.
class ProductImageIT extends IntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    Cookie manager;
    long product;

    @BeforeEach
    void setUp() throws Exception {
        manager = loginAs("MANAGER");
        product = createProduct(manager, createCategory(manager), "99.90", 5);
    }

    JsonNode upload(byte[] data, String view) throws Exception {
        return body(mvc.perform(multipart("/crm/products/" + product + "/images")
                        .file(new MockMultipartFile("file", "foto.bin", "application/octet-stream", data))
                        .param("view", view).cookie(manager))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    void gerenteMontaAGaleriaEACapaEPublica() throws Exception {
        long front = upload(JPG, "FRONT").get("id").asLong();
        long back = upload(PNG, "BACK").get("id").asLong();

        JsonNode list = body(mvc.perform(get("/crm/products/" + product + "/images").cookie(manager))
                .andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("view").asText()).isEqualTo("FRONT");
        assertThat(list.get(0).get("url").asText()).isEqualTo("/media/images/" + front);
        assertThat(list.get(1).get("source").asText()).startsWith("Enviada por");

        // Sem login: a vitrine e a venda mostram a foto.
        mvc.perform(get("/media/images/" + back)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png")).andExpect(content().bytes(PNG));
        mvc.perform(get("/media/products/" + product + "/cover")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        // A de trás vira capa; excluir a outra deixa só uma, na posição 0.
        mvc.perform(post("/crm/products/" + product + "/images/" + back + "/cover").cookie(manager)).andExpect(status().isNoContent());
        mvc.perform(get("/media/products/" + product + "/cover")).andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(delete("/crm/products/" + product + "/images/" + front).cookie(manager)).andExpect(status().isNoContent());
        JsonNode after = body(mvc.perform(get("/crm/products/" + product + "/images").cookie(manager)).andReturn());
        assertThat(after).hasSize(1);
        assertThat(after.get(0).get("position").asInt()).isZero();
    }

    @Test
    void recusaArquivoQueNaoEImagemVendedorEProdutoSemFoto() throws Exception {
        mvc.perform(multipart("/crm/products/" + product + "/images")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", "não sou imagem".getBytes()))
                        .cookie(manager))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_image"));
        mvc.perform(multipart("/crm/products/" + product + "/images")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", JPG)).cookie(loginAs("SELLER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/media/products/" + product + "/cover")).andExpect(status().isNotFound());
        mvc.perform(get("/crm/products/999999999/images").cookie(manager)).andExpect(status().isNotFound());
    }

    @Test
    void ateOitoImagensPorProduto() throws Exception {
        for (int i = 0; i < 8; i++) upload(JPG, "OTHER");
        mvc.perform(multipart("/crm/products/" + product + "/images")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", JPG)).cookie(manager))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("too_many_images"));
    }
}
