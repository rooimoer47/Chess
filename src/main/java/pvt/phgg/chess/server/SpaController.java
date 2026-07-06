package pvt.phgg.chess.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serve React SPA: real static files (JS, CSS, assets) are served as-is;
 * any path with no matching file falls back to index.html for client-side routing.
 * /api/** RestControllers and /ws/** WebSocket upgrades are unaffected — they
 * have higher handler priority than resource handlers.
 */
@Configuration
public class SpaController implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        return resource.exists() && resource.isReadable()
                                ? resource
                                : new ClassPathResource("static/index.html");
                    }
                });
    }
}
