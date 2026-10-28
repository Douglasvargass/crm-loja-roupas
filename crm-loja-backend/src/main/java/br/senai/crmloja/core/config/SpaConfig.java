package br.senai.crmloja.core.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

// Deploy único (Dockerfile): o build do front vai para classpath:/static e o próprio back-end serve as
// telas na mesma origem da API, sem proxy nem CORS. Rota desconhecida do front (ex.: /clientes/5 ao
// recarregar a página) devolve index.html para o React Router resolver. Em desenvolvimento não há
// static/index.html e nada muda: o front roda no Vite.
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    private static final String[] API_PREFIXES = {"auth/", "crm/", "admin/", "error"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) return file;
                        for (String prefix : API_PREFIXES) {
                            if (path.startsWith(prefix)) return null;   // rota de API inexistente continua 404
                        }
                        // Arquivo com extensão que não existe (ex.: /assets/x.js antigo) também é 404.
                        if (path.contains(".")) return null;
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
