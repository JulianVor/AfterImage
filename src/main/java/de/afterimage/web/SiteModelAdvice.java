package de.afterimage.web;

import de.afterimage.config.AfterimageProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SiteModelAdvice {

    private final AfterimageProperties properties;
    private final GermanLabels labels;

    public SiteModelAdvice(AfterimageProperties properties, GermanLabels labels) {
        this.properties = properties;
        this.labels = labels;
    }

    @ModelAttribute("site")
    public AfterimageProperties.Site site() {
        return properties.site();
    }

    @ModelAttribute("labels")
    public GermanLabels labels() {
        return labels;
    }
}
