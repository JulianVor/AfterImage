package de.afterimage.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Serves the "Regie" multicam easter egg's raw video/audio files straight off disk, bypassing the
 * MediaAsset/database flow - there's no curation need for a fixed, hand-picked set of camera angles,
 * and Spring's static resource handler already supports HTTP Range requests out of the box (needed for
 * several large videos to seek/play smoothly).
 */
@Configuration
public class RegieMediaConfig implements WebMvcConfigurer {

    private final AfterimageProperties properties;

    public RegieMediaConfig(AfterimageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + properties.media().root().toAbsolutePath().normalize() + "/regie/";
        registry.addResourceHandler("/regie-media/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable());
    }
}
