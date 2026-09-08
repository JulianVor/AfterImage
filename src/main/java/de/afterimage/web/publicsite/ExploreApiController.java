package de.afterimage.web.publicsite;

import de.afterimage.catalog.application.PublicCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/explore")
public class ExploreApiController {

    private final PublicCatalogService catalog;

    public ExploreApiController(PublicCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/neighborhood")
    PublicCatalogService.ExploreGraph neighborhood(@RequestParam(required = false) String focus) {
        return catalog.graph(focus);
    }

    @GetMapping("/bands")
    PublicCatalogService.BandNetwork bands() {
        return catalog.bandNetwork();
    }
}

